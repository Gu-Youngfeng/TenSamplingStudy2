/*
 *  CFQ, or complete fairness queueing, disk scheduler.
 *
 *  Based on ideas from a previously unfinished io
 *  scheduler (round robin per-process disk scheduling) and Andrea Arcangeli.
 *
 *  Copyright (C) 2003 Jens Axboe <axboe@kernel.dk>
 */
#include <linux/module.h>
#include <linux/slab.h>
#include <linux/blkdev.h>
#include <linux/elevator.h>
#include <linux/jiffies.h>
#include <linux/rbtree.h>
#include <linux/ioprio.h>
#include <linux/blktrace_api.h>
#include "blk.h"
#include "blk-cgroup.h"

static struct blkio_policy_type blkio_policy_cfq;

/*
 * tunables
 */
/* max queue in one round of service */
static const int cfq_quantum = 8;
static const int cfq_fifo_expire[2] = { HZ / 4, HZ / 8 };
/* maximum backwards seek, in KiB */
static const int cfq_back_max = 16 * 1024;
/* penalty of a backwards seek */
static const int cfq_back_penalty = 2;
static const int cfq_slice_sync = HZ / 10;
static int cfq_slice_async = HZ / 25;
static const int cfq_slice_async_rq = 2;
static int cfq_slice_idle = HZ / 125;
static int cfq_group_idle = HZ / 125;
static const int cfq_target_latency = HZ * 3/10; /* 300 ms */
static const int cfq_hist_divisor = 4;

/*
 * offset from end of service tree
 */
#define CFQ_IDLE_DELAY		(HZ / 5)

/*
 * below this threshold, we consider thinktime immediate
 */
#define CFQ_MIN_TT		(2)

#define CFQ_SLICE_SCALE		(5)
#define CFQ_HW_QUEUE_MIN	(5)
#define CFQ_SERVICE_SHIFT       12

#define CFQQ_SEEK_THR		(sector_t)(8 * 100)
#define CFQQ_CLOSE_THR		(sector_t)(8 * 1024)
#define CFQQ_SECT_THR_NONROT	(sector_t)(2 * 32)
#define CFQQ_SEEKY(cfqq)	(hweight32(cfqq->seek_history) > 32/8)

#define RQ_CIC(rq)		icq_to_cic((rq)->elv.icq)
#define RQ_CFQQ(rq)		(struct cfq_queue *) ((rq)->elv.priv[0])
#define RQ_CFQG(rq)		(struct cfq_group *) ((rq)->elv.priv[1])

static struct kmem_cache *cfq_pool;

#define CFQ_PRIO_LISTS		IOPRIO_BE_NR
#define cfq_class_idle(cfqq)	((cfqq)->ioprio_class == IOPRIO_CLASS_IDLE)
#define cfq_class_rt(cfqq)	((cfqq)->ioprio_class == IOPRIO_CLASS_RT)

#define sample_valid(samples)	((samples) > 80)
#define rb_entry_cfqg(node)	rb_entry((node), struct cfq_group, rb_node)

struct cfq_ttime {
	unsigned long last_end_request;

	unsigned long ttime_total;
	unsigned long ttime_samples;
	unsigned long ttime_mean;
};

/*
 * Most of our rbtree usage is for sorting with min extraction, so
 * if we cache the leftmost node we don't have to walk down the tree
 * to find it. Idea borrowed from Ingo Molnars CFS scheduler. We should
 * move this into the elevator for the rq sorting as well.
 */
struct cfq_rb_root {
	struct rb_root rb;
	struct rb_node *left;
	unsigned count;
	unsigned total_weight;
	u64 min_vdisktime;
	struct cfq_ttime ttime;
};
#define CFQ_RB_ROOT	(struct cfq_rb_root) { .rb = RB_ROOT, \
			.ttime = {.last_end_request = jiffies,},}

/*
 * Per process-grouping structure
 */
struct cfq_queue {
	/* reference count */
	int ref;
	/* various state flags, see below */
	unsigned int flags;
	/* parent cfq_data */
	struct cfq_data *cfqd;
	/* service_tree member */
	struct rb_node rb_node;
	/* service_tree key */
	unsigned long rb_key;
	/* prio tree member */
	struct rb_node p_node;
	/* prio tree root we belong to, if any */
	struct rb_root *p_root;
	/* sorted list of pending requests */
	struct rb_root sort_list;
	/* if fifo isn't expired, next request to serve */
	struct request *next_rq;
	/* requests queued in sort_list */
	int queued[2];
	/* currently allocated requests */
	int allocated[2];
	/* fifo list of requests in sort_list */
	struct list_head fifo;

	/* time when queue got scheduled in to dispatch first request. */
	unsigned long dispatch_start;
	unsigned int allocated_slice;
	unsigned int slice_dispatch;
	/* time when first request from queue completed and slice started. */
	unsigned long slice_start;
	unsigned long slice_end;
	long slice_resid;

	/* pending priority requests */
	int prio_pending;
	/* number of requests that are on the dispatch list or inside driver */
	int dispatched;

	/* io prio of this group */
	unsigned short ioprio, org_ioprio;
	unsigned short ioprio_class;

	pid_t pid;

	u32 seek_history;
	sector_t last_request_pos;

	struct cfq_rb_root *service_tree;
	struct cfq_queue *new_cfqq;
	struct cfq_group *cfqg;
	/* Number of sectors dispatched from queue in single dispatch round */
	unsigned long nr_sectors;
};

/*
 * First index in the service_trees.
 * IDLE is handled separately, so it has negative index
 */
enum wl_prio_t {
	BE_WORKLOAD = 0,
	RT_WORKLOAD = 1,
	IDLE_WORKLOAD = 2,
	CFQ_PRIO_NR,
};

/*
 * Second index in the service_trees.
 */
enum wl_type_t {
	ASYNC_WORKLOAD = 0,
	SYNC_NOIDLE_WORKLOAD = 1,
	SYNC_WORKLOAD = 2
};

struct cfqg_stats {
#ifdef CFQ_GROUP_IOSCHED
	/* total bytes transferred */
	struct blkg_rwstat		service_bytes;
	/* total IOs serviced, post merge */
	struct blkg_rwstat		serviced;
	/* number of ios merged */
	struct blkg_rwstat		merged;
	/* total time spent on device in ns, may not be accurate w/ queueing */
	struct blkg_rwstat		service_time;
	/* total time spent waiting in scheduler queue in ns */
	struct blkg_rwstat		wait_time;
	/* number of IOs queued up */
	struct blkg_rwstat		queued;
	/* total sectors transferred */
	struct blkg_stat		sectors;
	/* total disk time and nr sectors dispatched by this group */
	struct blkg_stat		time;
#ifdef DEBUG_BLK_CGROUP
	/* time not charged to this cgroup */
	struct blkg_stat		unaccounted_time;
	/* sum of number of ios queued across all samples */
	struct blkg_stat		avg_queue_size_sum;
	/* count of samples taken for average */
	struct blkg_stat		avg_queue_size_samples;
	/* how many times this group has been removed from service tree */
	struct blkg_stat		dequeue;
	/* total time spent waiting for it to be assigned a timeslice. */
	struct blkg_stat		group_wait_time;
	/* time spent idling for this blkio_group */
	struct blkg_stat		idle_time;
	/* total time with empty current active q with other requests queued */
	struct blkg_stat		empty_time;
	/* fields after this shouldn't be cleared on stat reset */
	uint64_t			start_group_wait_time;
	uint64_t			start_idle_time;
	uint64_t			start_empty_time;
	uint16_t			flags;
#endif	/* DEBUG_BLK_CGROUP */
#endif	/* CFQ_GROUP_IOSCHED */
};

/* This is per cgroup per device grouping structure */
struct cfq_group {
	/* group service_tree member */
	struct rb_node rb_node;

	/* group service_tree key */
	u64 vdisktime;
	unsigned int weight;
	unsigned int new_weight;
	unsigned int dev_weight;

	/* number of cfqq currently on this group */
	int nr_cfqq;

	/*
	 * Per group busy queues average. Useful for workload slice calc. We
	 * create the array for each prio class but at run time it is used
	 * only for RT and BE class and slot for IDLE class remains unused.
	 * This is primarily done to avoid confusion and a gcc warning.
	 */
	unsigned int busy_queues_avg[CFQ_PRIO_NR];
	/*
	 * rr lists of queues with requests. We maintain service trees for
	 * RT and BE classes. These trees are subdivided in subclasses
	 * of SYNC, SYNC_NOIDLE and ASYNC based on workload type. For IDLE
	 * class there is no subclassification and all the cfq queues go on
	 * a single tree service_tree_idle.
	 * Counts are embedded in the cfq_rb_root
	 */
	struct cfq_rb_root service_trees[2][3];
	struct cfq_rb_root service_tree_idle;

	unsigned long saved_workload_slice;
	enum wl_type_t saved_workload;
	enum wl_prio_t saved_serving_prio;

	/* number of requests that are on the dispatch list or inside driver */
	int dispatched;
	struct cfq_ttime ttime;
	struct cfqg_stats stats;
};

struct cfq_io_cq {
	struct io_cq		icq;		/* must be the first member */
	struct cfq_queue	*cfqq[2];
	struct cfq_ttime	ttime;
	int			ioprio;		/* the current ioprio */
#ifdef CFQ_GROUP_IOSCHED
	uint64_t		blkcg_id;	/* the current blkcg ID */
#endif
};

/*
 * Per block device queue structure
 */
struct cfq_data {
	struct request_queue *queue;
	/* Root service tree for cfq_groups */
	struct cfq_rb_root grp_service_tree;
	struct cfq_group *root_group;

	/*
	 * The priority currently being served
	 */
	enum wl_prio_t serving_prio;
	enum wl_type_t serving_type;
	unsigned long workload_expires;
	struct cfq_group *serving_group;

	/*
	 * Each priority tree is sorted by next_request position.  These
	 * trees are used when determining if two or more queues are
	 * interleaving requests (see cfq_close_cooperator).
	 */
	struct rb_root prio_trees[CFQ_PRIO_LISTS];

	unsigned int busy_queues;
	unsigned int busy_sync_queues;

	int rq_in_driver;
	int rq_in_flight[2];

	/*
	 * queue-depth detection
	 */
	int rq_queued;
	int hw_tag;
	/*
	 * hw_tag can be
	 * -1 => indeterminate, (cfq will behave as if NCQ is present, to allow better detection)
	 *  1 => NCQ is present (hw_tag_est_depth is the estimated max depth)
	 *  0 => no NCQ
	 */
	int hw_tag_est_depth;
	unsigned int hw_tag_samples;

	/*
	 * idle window management
	 */
	struct timer_list idle_slice_timer;
	struct work_struct unplug_work;

	struct cfq_queue *active_queue;
	struct cfq_io_cq *active_cic;

	/*
	 * async queue for each priority case
	 */
	struct cfq_queue *async_cfqq[2][IOPRIO_BE_NR];
	struct cfq_queue *async_idle_cfqq;

	sector_t last_position;

	/*
	 * tunables, see top of file
	 */
	unsigned int cfq_quantum;
	unsigned int cfq_fifo_expire[2];
	unsigned int cfq_back_penalty;
	unsigned int cfq_back_max;
	unsigned int cfq_slice[2];
	unsigned int cfq_slice_async_rq;
	unsigned int cfq_slice_idle;
	unsigned int cfq_group_idle;
	unsigned int cfq_latency;

	/*
	 * Fallback dummy cfqq for extreme OOM conditions
	 */
	struct cfq_queue oom_cfqq;

	unsigned long last_delayed_sync;
};

static struct cfq_group *cfq_get_next_cfqg(struct cfq_data *cfqd);

static struct cfq_rb_root *service_tree_for(struct cfq_group *cfqg,
					    enum wl_prio_t prio,
					    enum wl_type_t type)
{
	if (!cfqg)
		return NULL;

	if (prio == IDLE_WORKLOAD)
		return &cfqg->service_tree_idle;

	return &cfqg->service_trees[prio][type];
}

enum cfqq_state_flags {
	CFQ_CFQQ_FLAG_on_rr = 0,	/* on round-robin busy list */
	CFQ_CFQQ_FLAG_wait_request,	/* waiting for a request */
	CFQ_CFQQ_FLAG_must_dispatch,	/* must be allowed a dispatch */
	CFQ_CFQQ_FLAG_must_alloc_slice,	/* per-slice must_alloc flag */
	CFQ_CFQQ_FLAG_fifo_expire,	/* FIFO checked in this slice */
	CFQ_CFQQ_FLAG_idle_window,	/* slice idling enabled */
	CFQ_CFQQ_FLAG_prio_changed,	/* task priority has changed */
	CFQ_CFQQ_FLAG_slice_new,	/* no requests dispatched in slice */
	CFQ_CFQQ_FLAG_sync,		/* synchronous queue */
	CFQ_CFQQ_FLAG_coop,		/* cfqq is shared */
	CFQ_CFQQ_FLAG_split_coop,	/* shared cfqq will be splitted */
	CFQ_CFQQ_FLAG_deep,		/* sync cfqq experienced large depth */
	CFQ_CFQQ_FLAG_wait_busy,	/* Waiting for next request */
};

#define CFQ_CFQQ_FNS(name)						\
static inline void cfq_mark_cfqq_##name(struct cfq_queue *cfqq)		\
{									\
	(cfqq)->flags |= (1 << CFQ_CFQQ_FLAG_##name);			\
}									\
static inline void cfq_clear_cfqq_##name(struct cfq_queue *cfqq)	\
{									\
	(cfqq)->flags &= ~(1 << CFQ_CFQQ_FLAG_##name);			\
}									\
static inline int cfq_cfqq_##name(const struct cfq_queue *cfqq)		\
{									\
	return ((cfqq)->flags & (1 << CFQ_CFQQ_FLAG_##name)) != 0;	\
}

CFQ_CFQQ_FNS(on_rr);
CFQ_CFQQ_FNS(wait_request);
CFQ_CFQQ_FNS(must_dispatch);
CFQ_CFQQ_FNS(must_alloc_slice);
CFQ_CFQQ_FNS(fifo_expire);
CFQ_CFQQ_FNS(idle_window);
CFQ_CFQQ_FNS(prio_changed);
CFQ_CFQQ_FNS(slice_new);
CFQ_CFQQ_FNS(sync);
CFQ_CFQQ_FNS(coop);
CFQ_CFQQ_FNS(split_coop);
CFQ_CFQQ_FNS(deep);
CFQ_CFQQ_FNS(wait_busy);
#undef CFQ_CFQQ_FNS

#if defined(CFQ_GROUP_IOSCHED) && defined(DEBUG_BLK_CGROUP)

/* cfqg stats flags */
enum cfqg_stats_flags {
	CFQG_stats_waiting = 0,
	CFQG_stats_idling,
	CFQG_stats_empty,
};

#define CFQG_FLAG_FNS(name)						\
static inline void cfqg_stats_mark_##name(struct cfqg_stats *stats)	\
{									\
	stats->flags |= (1 << CFQG_stats_##name);			\
}									\
static inline void cfqg_stats_clear_##name(struct cfqg_stats *stats)	\
{									\
	stats->flags &= ~(1 << CFQG_stats_##name);			\
}									\
static inline int cfqg_stats_##name(struct cfqg_stats *stats)		\
{									\
	return (stats->flags & (1 << CFQG_stats_##name)) != 0;		\
}									\

CFQG_FLAG_FNS(waiting)
CFQG_FLAG_FNS(idling)
CFQG_FLAG_FNS(empty)
#undef CFQG_FLAG_FNS

/* This should be called with the queue_lock held. */
static void cfqg_stats_update_group_wait_time(struct cfqg_stats *stats)
{
	unsigned long long now;

	if (!cfqg_stats_waiting(stats))
		return;

	now = sched_clock();
	if (time_after64(now, stats->start_group_wait_time))
		blkg_stat_add(&stats->group_wait_time,
			      now - stats->start_group_wait_time);
	cfqg_stats_clear_waiting(stats);
}

/* This should be called with the queue_lock held. */
static void cfqg_stats_set_start_group_wait_time(struct cfq_group *cfqg,
						 struct cfq_group *curr_cfqg)
{
	struct cfqg_stats *stats = &cfqg->stats;

	if (cfqg_stats_waiting(stats))
		return;
	if (cfqg == curr_cfqg)
		return;
	stats->start_group_wait_time = sched_clock();
	cfqg_stats_mark_waiting(stats);
}

/* This should be called with the queue_lock held. */
static void cfqg_stats_end_empty_time(struct cfqg_stats *stats)
{
	unsigned long long now;

	if (!cfqg_stats_empty(stats))
		return;

	now = sched_clock();
	if (time_after64(now, stats->start_empty_time))
		blkg_stat_add(&stats->empty_time,
			      now - stats->start_empty_time);
	cfqg_stats_clear_empty(stats);
}

static void cfqg_stats_update_dequeue(struct cfq_group *cfqg)
{
	blkg_stat_add(&cfqg->stats.dequeue, 1);
}

static void cfqg_stats_set_start_empty_time(struct cfq_group *cfqg)
{
	struct cfqg_stats *stats = &cfqg->stats;

	if (blkg_rwstat_sum(&stats->queued))
		return;

	/*
	 * group is already marked empty. This can happen if cfqq got new
	 * request in parent group and moved to this group while being added
	 * to service tree. Just ignore the event and move on.
	 */
	if (cfqg_stats_empty(stats))
		return;

	stats->start_empty_time = sched_clock();
	cfqg_stats_mark_empty(stats);
}

static void cfqg_stats_update_idle_time(struct cfq_group *cfqg)
{
	struct cfqg_stats *stats = &cfqg->stats;

	if (cfqg_stats_idling(stats)) {
		unsigned long long now = sched_clock();

		if (time_after64(now, stats->start_idle_time))
			blkg_stat_add(&stats->idle_time,
				      now - stats->start_idle_time);
		cfqg_stats_clear_idling(stats);
	}
}

static void cfqg_stats_set_start_idle_time(struct cfq_group *cfqg)
{
	struct cfqg_stats *stats = &cfqg->stats;

	BUG_ON(cfqg_stats_idling(stats));

	stats->start_idle_time = sched_clock();
	cfqg_stats_mark_idling(stats);
}

static void cfqg_stats_update_avg_queue_size(struct cfq_group *cfqg)
{
	struct cfqg_stats *stats = &cfqg->stats;

	blkg_stat_add(&stats->avg_queue_size_sum,
		      blkg_rwstat_sum(&stats->queued));
	blkg_stat_add(&stats->avg_queue_size_samples, 1);
	cfqg_stats_update_group_wait_time(stats);
}

#else	/* CFQ_GROUP_IOSCHED && DEBUG_BLK_CGROUP */

static inline void cfqg_stats_set_start_group_wait_time(struct cfq_group *cfqg, struct cfq_group *curr_cfqg) { }
static inline void cfqg_stats_end_empty_time(struct cfqg_stats *stats) { }
static inline void cfqg_stats_update_dequeue(struct cfq_group *cfqg) { }
static inline void cfqg_stats_set_start_empty_time(struct cfq_group *cfqg) { }
static inline void cfqg_stats_update_idle_time(struct cfq_group *cfqg) { }
static inline void cfqg_stats_set_start_idle_time(struct cfq_group *cfqg) { }
static inline void cfqg_stats_update_avg_queue_size(struct cfq_group *cfqg) { }

#endif	/* CFQ_GROUP_IOSCHED && DEBUG_BLK_CGROUP */

#ifdef CFQ_GROUP_IOSCHED

static inline struct cfq_group *blkg_to_cfqg(struct blkio_group *blkg)
{
	return blkg_to_pdata(blkg, &blkio_policy_cfq);
}

static inline struct blkio_group *cfqg_to_blkg(struct cfq_group *cfqg)
{
	return pdata_to_blkg(cfqg);
}

static inline void cfqg_get(struct cfq_group *cfqg)
{
	return blkg_get(cfqg_to_blkg(cfqg));
}

static inline void cfqg_put(struct cfq_group *cfqg)
{
	return blkg_put(cfqg_to_blkg(cfqg));
}

#define cfq_log_cfqq(cfqd, cfqq, fmt, args...)	\
	blk_add_trace_msg((cfqd)->queue, "cfq%d%c %s " fmt, (cfqq)->pid, \
			cfq_cfqq_sync((cfqq)) ? 'S' : 'A', \
			blkg_path(cfqg_to_blkg((cfqq)->cfqg)), ##args)

#define cfq_log_cfqg(cfqd, cfqg, fmt, args...)				\
	blk_add_trace_msg((cfqd)->queue, "%s " fmt,			\
			blkg_path(cfqg_to_blkg((cfqg))), ##args)	\

static inline void cfqg_stats_update_io_add(struct cfq_group *cfqg,
					    struct cfq_group *curr_cfqg, int rw)
{
	blkg_rwstat_add(&cfqg->stats.queued, rw, 1);
	cfqg_stats_end_empty_time(&cfqg->stats);
	cfqg_stats_set_start_group_wait_time(cfqg, curr_cfqg);
}

static inline void cfqg_stats_update_timeslice_used(struct cfq_group *cfqg,
			unsigned long time, unsigned long unaccounted_time)
{
	blkg_stat_add(&cfqg->stats.time, time);
#ifdef DEBUG_BLK_CGROUP
	blkg_stat_add(&cfqg->stats.unaccounted_time, unaccounted_time);
#endif
}

static inline void cfqg_stats_update_io_remove(struct cfq_group *cfqg, int rw)
{
	blkg_rwstat_add(&cfqg->stats.queued, rw, -1);
}

static inline void cfqg_stats_update_io_merged(struct cfq_group *cfqg, int rw)
{
	blkg_rwstat_add(&cfqg->stats.merged, rw, 1);
}

static inline void cfqg_stats_update_dispatch(struct cfq_group *cfqg,
					      uint64_t bytes, int rw)
{
	blkg_stat_add(&cfqg->stats.sectors, bytes >> 9);
	blkg_rwstat_add(&cfqg->stats.serviced, rw, 1);
	blkg_rwstat_add(&cfqg->stats.service_bytes, rw, bytes);
}

static inline void cfqg_stats_update_completion(struct cfq_group *cfqg,
			uint64_t start_time, uint64_t io_start_time, int rw)
{
	struct cfqg_stats *stats = &cfqg->stats;
	unsigned long long now = sched_clock();

	if (time_after64(now, io_start_time))
		blkg_rwstat_add(&stats->service_time, rw, now - io_start_time);
	if (time_after64(io_start_time, start_time))
		blkg_rwstat_add(&stats->wait_time, rw,
				io_start_time - start_time);
}

static void cfqg_stats_reset(struct blkio_group *blkg)
{
	struct cfq_group *cfqg = blkg_to_cfqg(blkg);
	struct cfqg_stats *stats = &cfqg->stats;

	/* queued stats shouldn't be cleared */
	blkg_rwstat_reset(&stats->service_bytes);
	blkg_rwstat_reset(&stats->serviced);
	blkg_rwstat_reset(&stats->merged);
	blkg_rwstat_reset(&stats->service_time);
	blkg_rwstat_reset(&stats->wait_time);
	blkg_stat_reset(&stats->time);
#ifdef DEBUG_BLK_CGROUP
	blkg_stat_reset(&stats->unaccounted_time);
	blkg_stat_reset(&stats->avg_queue_size_sum);
	blkg_stat_reset(&stats->avg_queue_size_samples);
	blkg_stat_reset(&stats->dequeue);
	blkg_stat_reset(&stats->group_wait_time);
	blkg_stat_reset(&stats->idle_time);
	blkg_stat_reset(&stats->empty_time);
#endif
}

#else	/* CFQ_GROUP_IOSCHED */

static inline struct cfq_group *blkg_to_cfqg(struct blkio_group *blkg) { return NULL; }
static inline struct blkio_group *cfqg_to_blkg(struct cfq_group *cfqg) { return NULL; }
static inline void cfqg_get(struct cfq_group *cfqg) { }
static inline void cfqg_put(struct cfq_group *cfqg) { }

#define cfq_log_cfqq(cfqd, cfqq, fmt, args...)	\
	blk_add_trace_msg((cfqd)->queue, "cfq%d " fmt, (cfqq)->pid, ##args)
#define cfq_log_cfqg(cfqd, cfqg, fmt, args...)		do {} while (0)

static inline void cfqg_stats_update_io_add(struct cfq_group *cfqg,
			struct cfq_group *curr_cfqg, int rw) { }
static inline void cfqg_stats_update_timeslice_used(struct cfq_group *cfqg,
			unsigned long time, unsigned long unaccounted_time) { }
static inline void cfqg_stats_update_io_remove(struct cfq_group *cfqg, int rw) { }
static inline void cfqg_stats_update_io_merged(struct cfq_group *cfqg, int rw) { }
static inline void cfqg_stats_update_dispatch(struct cfq_group *cfqg,
					      uint64_t bytes, int rw) { }
static inline void cfqg_stats_update_completion(struct cfq_group *cfqg,
			uint64_t start_time, uint64_t io_start_time, int rw) { }

#endif	/* CFQ_GROUP_IOSCHED */

#define cfq_log(cfqd, fmt, args...)	\
	blk_add_trace_msg((cfqd)->queue, "cfq " fmt, ##args)

/* Traverses through cfq group service trees */
#define for_each_cfqg_st(cfqg, i, j, st) \
	for (i = 0; i <= IDLE_WORKLOAD; i++) \
		for (j = 0, st = i < IDLE_WORKLOAD ? &cfqg->service_trees[i][j]\
			: &cfqg->service_tree_idle; \
			(i < IDLE_WORKLOAD && j <= SYNC_WORKLOAD) || \
			(i == IDLE_WORKLOAD && j == 0); \
			j++, st = i < IDLE_WORKLOAD ? \
			&cfqg->service_trees[i][j]: NULL) \

static inline bool cfq_io_thinktime_big(struct cfq_data *cfqd,
	struct cfq_ttime *ttime, bool group_idle)
{
	unsigned long slice;
	if (!sample_valid(ttime->ttime_samples))
		return false;
	if (group_idle)
		slice = cfqd->cfq_group_idle;
	else
		slice = cfqd->cfq_slice_idle;
	return ttime->ttime_mean > slice;
}

static inline bool iops_mode(struct cfq_data *cfqd)
{
	/*
	 * If we are not idling on queues and it is a NCQ drive, parallel
	 * execution of requests is on and measuring time is not possible
	 * in most of the cases until and unless we drive shallower queue
	 * depths and that becomes a performance bottleneck. In such cases
	 * switch to start providing fairness in terms of number of IOs.
	 */
	if (!cfqd->cfq_slice_idle && cfqd->hw_tag)
		return true;
	else
		return false;
}

static inline enum wl_prio_t cfqq_prio(struct cfq_queue *cfqq)
{
	if (cfq_class_idle(cfqq))
		return IDLE_WORKLOAD;
	if (cfq_class_rt(cfqq))
		return RT_WORKLOAD;
	return BE_WORKLOAD;
}


static enum wl_type_t cfqq_type(struct cfq_queue *cfqq)
{
	if (!cfq_cfqq_sync(cfqq))
		return ASYNC_WORKLOAD;
	if (!cfq_cfqq_idle_window(cfqq))
		return SYNC_NOIDLE_WORKLOAD;
	return SYNC_WORKLOAD;
}

static inline int cfq_group_busy_queues_wl(enum wl_prio_t wl,
					struct cfq_data *cfqd,
					struct cfq_group *cfqg)
{
	if (wl == IDLE_WORKLOAD)
		return cfqg->service_tree_idle.count;

	return cfqg->service_trees[wl][ASYNC_WORKLOAD].count
		+ cfqg->service_trees[wl][SYNC_NOIDLE_WORKLOAD].count
		+ cfqg->service_trees[wl][SYNC_WORKLOAD].count;
}

static inline int cfqg_busy_async_queues(struct cfq_data *cfqd,
					struct cfq_group *cfqg)
{
	return cfqg->service_trees[RT_WORKLOAD][ASYNC_WORKLOAD].count
		+ cfqg->service_trees[BE_WORKLOAD][ASYNC_WORKLOAD].count;
}

static void cfq_dispatch_insert(struct request_queue *, struct request *);
static struct cfq_queue *cfq_get_queue(struct cfq_data *cfqd, bool is_sync,
				       struct cfq_io_cq *cic, struct bio *bio,
				       gfp_t gfp_mask);

static inline struct cfq_io_cq *icq_to_cic(struct io_cq *icq)
{
	/* cic->icq is the first member, %NULL will convert to %NULL */
	//return container_of(icq, struct cfq_io_cq, icq);
}

static inline struct cfq_io_cq *cfq_cic_lookup(struct cfq_data *cfqd,
					       struct io_context *ioc)
{
	if (ioc)
		return icq_to_cic(ioc_lookup_icq(ioc, cfqd->queue));
	return NULL;
}

static inline struct cfq_queue *cic_to_cfqq(struct cfq_io_cq *cic, bool is_sync)
{
	return cic->cfqq[is_sync];
}

static inline void cic_set_cfqq(struct cfq_io_cq *cic, struct cfq_queue *cfqq,
				bool is_sync)
{
	cic->cfqq[is_sync] = cfqq;
}

static inline struct cfq_data *cic_to_cfqd(struct cfq_io_cq *cic)
{
	return cic->icq.q->elevator->elevator_data;
}

/*
 * We regard a request as SYNC, if it's either a read or has the SYNC bit
 * set (in which case it could also be direct WRITE).
 */
static inline bool cfq_bio_sync(struct bio *bio)
{
	return bio_data_dir(bio) == READ || (bio->bi_rw & REQ_SYNC);
}

/*
 * scheduler run of queue, if there are requests pending and no one in the
 * driver that will restart queueing
 */
static inline void cfq_schedule_dispatch(struct cfq_data *cfqd)
{
	if (cfqd->busy_queues) {
		cfq_log(cfqd, "schedule dispatch");
		kblockd_schedule_work(cfqd->queue, &cfqd->unplug_work);
	}
}

/*
 * Scale schedule slice based on io priority. Use the sync time slice only
 * if a queue is marked sync and has sync io queued. A sync queue with async
 * io only, should not get full sync slice length.
 */
static inline int cfq_prio_slice(struct cfq_data *cfqd, bool sync,
				 unsigned short prio)
{
	const int base_slice = cfqd->cfq_slice[sync];

	WARN_ON(prio >= IOPRIO_BE_NR);

	return base_slice + (base_slice/CFQ_SLICE_SCALE * (4 - prio));
}

static inline int
cfq_prio_to_slice(struct cfq_data *cfqd, struct cfq_queue *cfqq)
{
	return cfq_prio_slice(cfqd, cfq_cfqq_sync(cfqq), cfqq->ioprio);
}

static inline u64 cfq_scale_slice(unsigned long delta, struct cfq_group *cfqg)
{
	u64 d = delta << CFQ_SERVICE_SHIFT;

	d = d * CFQ_WEIGHT_DEFAULT;
	do_div(d, cfqg->weight);
	return d;
}



/*
 * get averaged number of queues of RT/BE priority.
 * average is updated, with a formula that gives more weight to higher numbers,
 * to quickly follows sudden increases and decrease slowly
 */

static inline unsigned cfq_group_get_avg_queues(struct cfq_data *cfqd,
					struct cfq_group *cfqg, bool rt)
{
	unsigned min_q, max_q;
	unsigned mult  = cfq_hist_divisor - 1;
	unsigned round = cfq_hist_divisor / 2;
	unsigned busy = cfq_group_busy_queues_wl(rt, cfqd, cfqg);

	min_q = min(cfqg->busy_queues_avg[rt], busy);
	max_q = max(cfqg->busy_queues_avg[rt], busy);
	cfqg->busy_queues_avg[rt] = (mult * max_q + min_q + round) /
		cfq_hist_divisor;
	return cfqg->busy_queues_avg[rt];
}

static inline unsigned
cfq_group_slice(struct cfq_data *cfqd, struct cfq_group *cfqg)
{
	struct cfq_rb_root *st = &cfqd->grp_service_tree;

	return cfq_target_latency * cfqg->weight / st->total_weight;
}

static inline unsigned
cfq_scaled_cfqq_slice(struct cfq_data *cfqd, struct cfq_queue *cfqq)
{
	unsigned slice = cfq_prio_to_slice(cfqd, cfqq);
	if (cfqd->cfq_latency) {
		/*
		 * interested queues (we consider only the ones with the same
		 * priority class in the cfq group)
		 */
		unsigned iq = cfq_group_get_avg_queues(cfqd, cfqq->cfqg,
						cfq_class_rt(cfqq));
		unsigned sync_slice = cfqd->cfq_slice[1];
		unsigned expect_latency = sync_slice * iq;
		unsigned group_slice = cfq_group_slice(cfqd, cfqq->cfqg);

		if (expect_latency > group_slice) {
			unsigned base_low_slice = 2 * cfqd->cfq_slice_idle;
			/* scale low_slice according to IO priority
			 * and sync vs async */
			unsigned low_slice =
				min(slice, base_low_slice * slice / sync_slice);
			/* the adapted slice value is scaled to fit all iqs
			 * into the target latency */
			slice = max(slice * group_slice / expect_latency,
				    low_slice);
		}
	}
	return slice;
}

static inline void
cfq_set_prio_slice(struct cfq_data *cfqd, struct cfq_queue *cfqq)
{
	unsigned slice = cfq_scaled_cfqq_slice(cfqd, cfqq);

	cfqq->slice_start = jiffies;
	cfqq->slice_end = jiffies + slice;
	cfqq->allocated_slice = slice;
	cfq_log_cfqq(cfqd, cfqq, "set_slice=%lu", cfqq->slice_end - jiffies);
}

/*
 * We need to wrap this check in cfq_cfqq_slice_new(), since ->slice_end
 * isn't valid until the first request from the dispatch is activated
 * and the slice time set.
 */
static inline bool cfq_slice_used(struct cfq_queue *cfqq)
{
	if (cfq_cfqq_slice_new(cfqq))
		return false;
	if (time_before(jiffies, cfqq->slice_end))
		return false;

	return true;
}

/*
 * Lifted from AS - choose which of rq1 and rq2 that is best served now.
 * We choose the request that is closest to the head right now. Distance
 * behind the head is penalized and only allowed to a certain extent.
 */
static struct request *
cfq_choose_req(struct cfq_data *cfqd, struct request *rq1, struct request *rq2, sector_t last)
{
	sector_t s1, s2, d1 = 0, d2 = 0;
	unsigned long back_max;
#define CFQ_RQ1_WRAP	0x01 /* request 1 wraps */
#define CFQ_RQ2_WRAP	0x02 /* request 2 wraps */
	unsigned wrap = 0; /* bit mask: requests behind the disk head? */

	if (rq1 == NULL || rq1 == rq2)
		return rq2;
	if (rq2 == NULL)
		return rq1;

	if (rq_is_sync(rq1) != rq_is_sync(rq2))
		return rq_is_sync(rq1) ? rq1 : rq2;

	if ((rq1->cmd_flags ^ rq2->cmd_flags) & REQ_PRIO)
		return rq1->cmd_flags & REQ_PRIO ? rq1 : rq2;

	s1 = blk_rq_pos(rq1);
	s2 = blk_rq_pos(rq2);

	/*
	 * by definition, 1KiB is 2 sectors
	 */
	back_max = cfqd->cfq_back_max * 2;

	/*
	 * Strict one way elevator _except_ in the case where we allow
	 * short backward seeks which are biased as twice the cost of a
	 * similar forward seek.
	 */
	if (s1 >= last)
		d1 = s1 - last;
	else if (s1 + back_max >= last)
		d1 = (last - s1) * cfqd->cfq_back_penalty;
	else
		wrap |= CFQ_RQ1_WRAP;

	if (s2 >= last)
		d2 = s2 - last;
	else if (s2 + back_max >= last)
		d2 = (last - s2) * cfqd->cfq_back_penalty;
	else
		wrap |= CFQ_RQ2_WRAP;

	/* Found required data */

	/*
	 * By doing switch() on the bit mask "wrap" we avoid having to
	 * check two variables for all permutations: --> faster!
	 */
	switch (wrap) {
	case 0: /* common case for CFQ: rq1 and rq2 not wrapped */
		if (d1 < d2)
			return rq1;
		else if (d2 < d1)
			return rq2;
		else {
			if (s1 >= s2)
				return rq1;
			else
				return rq2;
		}

	case CFQ_RQ2_WRAP:
		return rq1;
	case CFQ_RQ1_WRAP:
		return rq2;
	case (CFQ_RQ1_WRAP|CFQ_RQ2_WRAP): /* both rqs wrapped */
	default:
		/*
		 * Since both rqs are wrapped,
		 * start with the one that's further behind head
		 * (--> only *one* back seek required),
		 * since back seek takes more time than forward.
		 */
		if (s1 <= s2)
			return rq1;
		else
			return rq2;
	}
}

/*
 * The below is leftmost cache rbtree addon
 */
static struct cfq_queue *cfq_rb_first(struct cfq_rb_root *root)
{
	/* Service tree is empty */
	if (!root->count)
		return NULL;

	if (!root->left)
		root->left = rb_first(&root->rb);

	//if (root->left)
		//return rb_entry(root->left, struct cfq_queue, rb_node);

	return NULL;
}



/*
 * would be nice to take fifo expire time into account as well
 */
static struct request *
cfq_find_next_rq(struct cfq_data *cfqd, struct cfq_queue *cfqq,
		  struct request *last)
{
	struct rb_node *rbnext = rb_next(&last->rb_node);
	struct rb_node *rbprev = rb_prev(&last->rb_node);
	struct request *next = NULL, *prev = NULL;

	BUG_ON(RB_EMPTY_NODE(&last->rb_node));

	if (rbprev)
		prev = rb_entry_rq(rbprev);

	if (rbnext)
		next = rb_entry_rq(rbnext);
	else {
		rbnext = rb_first(&cfqq->sort_list);
		if (rbnext && rbnext != &last->rb_node)
			next = rb_entry_rq(rbnext);
	}

	return cfq_choose_req(cfqd, next, prev, blk_rq_pos(last));
}

static unsigned long cfq_slice_offset(struct cfq_data *cfqd,
				      struct cfq_queue *cfqq)
{
	/*
	 * just an approximation, should be ok.
	 */
	return (cfqq->cfqg->nr_cfqq - 1) * (cfq_prio_slice(cfqd, 1, 0) -
		       cfq_prio_slice(cfqd, cfq_cfqq_sync(cfqq), cfqq->ioprio));
}

static inline s64
cfqg_key(struct cfq_rb_root *st, struct cfq_group *cfqg)
{
	return cfqg->vdisktime - st->min_vdisktime;
}

static void
__cfq_group_service_tree_add(struct cfq_rb_root *st, struct cfq_group *cfqg)
{
	struct rb_node **node = &st->rb.rb_node;
	struct rb_node *parent = NULL;
	struct cfq_group *__cfqg;
	s64 key = cfqg_key(st, cfqg);
	int left = 1;


}

static void
cfq_update_group_weight(struct cfq_group *cfqg)
{
	BUG_ON(!RB_EMPTY_NODE(&cfqg->rb_node));
	if (cfqg->new_weight) {
		cfqg->weight = cfqg->new_weight;
		cfqg->new_weight = 0;
	}
}

static void
cfq_group_service_tree_add(struct cfq_rb_root *st, struct cfq_group *cfqg)
{
	BUG_ON(!RB_EMPTY_NODE(&cfqg->rb_node));

	cfq_update_group_weight(cfqg);
	__cfq_group_service_tree_add(st, cfqg);
	st->total_weight += cfqg->weight;
}


static void
cfq_group_notify_queue_del(struct cfq_data *cfqd, struct cfq_group *cfqg)
{
	struct cfq_rb_root *st = &cfqd->grp_service_tree;

	BUG_ON(cfqg->nr_cfqq < 1);
	cfqg->nr_cfqq--;

	/* If there are other cfq queues under this group, don't delete it */
	if (cfqg->nr_cfqq)
		return;

	cfq_log_cfqg(cfqd, cfqg, "del_from_rr group");
	cfq_group_service_tree_del(st, cfqg);
	cfqg->saved_workload_slice = 0;
	cfqg_stats_update_dequeue(cfqg);
}



static void cfq_group_served(struct cfq_data *cfqd, struct cfq_group *cfqg,
				struct cfq_queue *cfqq)
{
	struct cfq_rb_root *st = &cfqd->grp_service_tree;
	unsigned int used_sl, charge, unaccounted_sl = 0;
	int nr_sync = cfqg->nr_cfqq - cfqg_busy_async_queues(cfqd, cfqg)
			- cfqg->service_tree_idle.count;

	BUG_ON(nr_sync < 0);
	used_sl = charge = cfq_cfqq_slice_usage(cfqq, &unaccounted_sl);

	if (iops_mode(cfqd))
		charge = cfqq->slice_dispatch;
	else if (!cfq_cfqq_sync(cfqq) && !nr_sync)
		charge = cfqq->allocated_slice;

	/* Can't update vdisktime while group is on service tree */
	cfq_group_service_tree_del(st, cfqg);
	cfqg->vdisktime += cfq_scale_slice(charge, cfqg);
	/* If a new weight was requested, update now, off tree */
	cfq_group_service_tree_add(st, cfqg);

	/* This group is being expired. Save the context */
	if (time_after(cfqd->workload_expires, jiffies)) {
		cfqg->saved_workload_slice = cfqd->workload_expires
						- jiffies;
		cfqg->saved_workload = cfqd->serving_type;
		cfqg->saved_serving_prio = cfqd->serving_prio;
	} else
		cfqg->saved_workload_slice = 0;

	cfq_log_cfqg(cfqd, cfqg, "served: vt=%llu min_vt=%llu", cfqg->vdisktime,
					st->min_vdisktime);
	cfq_log_cfqq(cfqq->cfqd, cfqq,
		     "sl_used=%u disp=%u charge=%u iops=%u sect=%lu",
		     used_sl, cfqq->slice_dispatch, charge,
		     iops_mode(cfqd), cfqq->nr_sectors);
	cfqg_stats_update_timeslice_used(cfqg, used_sl, unaccounted_sl);
	cfqg_stats_set_start_empty_time(cfqg);
}

/**
 * cfq_init_cfqg_base - initialize base part of a cfq_group
 * @cfqg: cfq_group to initialize
 *
 * Initialize the base part which is used whether %CFQ_GROUP_IOSCHED
 * is enabled or not.
 */
static void cfq_init_cfqg_base(struct cfq_group *cfqg)
{
	struct cfq_rb_root *st;
	int i, j;

	for_each_cfqg_st(cfqg, i, j, st)
		*st = CFQ_RB_ROOT;
	RB_CLEAR_NODE(&cfqg->rb_node);

	cfqg->ttime.last_end_request = jiffies;
}

#ifdef CFQ_GROUP_IOSCHED
static void cfq_init_blkio_group(struct blkio_group *blkg)
{
	struct cfq_group *cfqg = blkg_to_cfqg(blkg);

	cfq_init_cfqg_base(cfqg);
	cfqg->weight = blkg->blkcg->cfq_weight;
}

/*
 * Search for the cfq group current task belongs to. request_queue lock must
 * be held.
 */
static struct cfq_group *cfq_lookup_create_cfqg(struct cfq_data *cfqd,
						struct blkio_cgroup *blkcg)
{
	struct request_queue *q = cfqd->queue;
	struct cfq_group *cfqg = NULL;

	/* avoid lookup for the common case where there's no blkio cgroup */
	if (blkcg == &blkio_root_cgroup) {
		cfqg = cfqd->root_group;
	} else {
		struct blkio_group *blkg;

		blkg = blkg_lookup_create(blkcg, q, false);
		if (!IS_ERR(blkg))
			cfqg = blkg_to_cfqg(blkg);
	}

	return cfqg;
}

static void cfq_link_cfqq_cfqg(struct cfq_queue *cfqq, struct cfq_group *cfqg)
{
	/* Currently, all async queues are mapped to root group */
	if (!cfq_cfqq_sync(cfqq))
		cfqg = cfqq->cfqd->root_group;

	cfqq->cfqg = cfqg;
	/* cfqq reference on cfqg */
	cfqg_get(cfqg);
}

static u64 cfqg_prfill_weight_device(struct seq_file *sf, void *pdata, int off)
{
	struct cfq_group *cfqg = pdata;

	if (!cfqg->dev_weight)
		return 0;
	return __blkg_prfill_u64(sf, pdata, cfqg->dev_weight);
}

static int cfqg_print_weight_device(struct cgroup *cgrp, struct cftype *cft,
				    struct seq_file *sf)
{
	blkcg_print_blkgs(sf, cgroup_to_blkio_cgroup(cgrp),
			  cfqg_prfill_weight_device, BLKIO_POLICY_PROP, 0,
			  false);
	return 0;
}

static int cfq_print_weight(struct cgroup *cgrp, struct cftype *cft,
			    struct seq_file *sf)
{
	seq_printf(sf, "%u\n", cgroup_to_blkio_cgroup(cgrp)->cfq_weight);
	return 0;
}

static int cfqg_set_weight_device(struct cgroup *cgrp, struct cftype *cft,
				  const char *buf)
{
	struct blkio_cgroup *blkcg = cgroup_to_blkio_cgroup(cgrp);
	struct blkg_conf_ctx ctx;
	struct cfq_group *cfqg;
	int ret;

	ret = blkg_conf_prep(blkcg, buf, &ctx);
	if (ret)
		return ret;

	ret = -EINVAL;
	cfqg = blkg_to_cfqg(ctx.blkg);
	if (cfqg && (!ctx.v || (ctx.v >= CFQ_WEIGHT_MIN &&
				ctx.v <= CFQ_WEIGHT_MAX))) {
		cfqg->dev_weight = ctx.v;
		cfqg->new_weight = cfqg->dev_weight ?: blkcg->cfq_weight;
		ret = 0;
	}

	blkg_conf_finish(&ctx);
	return ret;
}

static int cfq_set_weight(struct cgroup *cgrp, struct cftype *cft, u64 val)
{
	struct blkio_cgroup *blkcg = cgroup_to_blkio_cgroup(cgrp);
	struct blkio_group *blkg;
	struct hlist_node *n;

	if (val < CFQ_WEIGHT_MIN || val > CFQ_WEIGHT_MAX)
		return -EINVAL;

	spin_lock_irq(&blkcg->lock);
	blkcg->cfq_weight = (unsigned int)val;

	//hlist_for_each_entry(blkg, n, &blkcg->blkg_list, blkcg_node) {
		struct cfq_group *cfqg = blkg_to_cfqg(blkg);

		if (cfqg && !cfqg->dev_weight)
			cfqg->new_weight = blkcg->cfq_weight;
	//}

	spin_unlock_irq(&blkcg->lock);
	return 0;
}

static int cfqg_print_stat(struct cgroup *cgrp, struct cftype *cft,
			   struct seq_file *sf)
{
	struct blkio_cgroup *blkcg = cgroup_to_blkio_cgroup(cgrp);

	blkcg_print_blkgs(sf, blkcg, blkg_prfill_stat, BLKIO_POLICY_PROP,
			  cft->private, false);
	return 0;
}

static int cfqg_print_rwstat(struct cgroup *cgrp, struct cftype *cft,
			     struct seq_file *sf)
{
	struct blkio_cgroup *blkcg = cgroup_to_blkio_cgroup(cgrp);

	blkcg_print_blkgs(sf, blkcg, blkg_prfill_rwstat, BLKIO_POLICY_PROP,
			  cft->private, true);
	return 0;
}

#ifdef DEBUG_BLK_CGROUP
static u64 cfqg_prfill_avg_queue_size(struct seq_file *sf, void *pdata, int off)
{
	struct cfq_group *cfqg = pdata;
	u64 samples = blkg_stat_read(&cfqg->stats.avg_queue_size_samples);
	u64 v = 0;

	if (samples) {
		v = blkg_stat_read(&cfqg->stats.avg_queue_size_sum);
		do_div(v, samples);
	}
	__blkg_prfill_u64(sf, pdata, v);
	return 0;
}

/* print avg_queue_size */
static int cfqg_print_avg_queue_size(struct cgroup *cgrp, struct cftype *cft,
				     struct seq_file *sf)
{
	struct blkio_cgroup *blkcg = cgroup_to_blkio_cgroup(cgrp);

	blkcg_print_blkgs(sf, blkcg, cfqg_prfill_avg_queue_size,
			  BLKIO_POLICY_PROP, 0, false);
	return 0;
}
#endif	/* DEBUG_BLK_CGROUP */



#else /* GROUP_IOSCHED */
static struct cfq_group *cfq_lookup_create_cfqg(struct cfq_data *cfqd,
						struct blkio_cgroup *blkcg)
{
#ifdef DEBUG_BLK_CGROUP
	return cfqd->root_group;
#endif
}

static inline void
cfq_link_cfqq_cfqg(struct cfq_queue *cfqq, struct cfq_group *cfqg) {
	cfqq->cfqg = cfqg;
}

#endif /* GROUP_IOSCHED */



/*
 * add to busy list of queues for service, trying to be fair in ordering
 * the pending list according to last request service
 */
static void cfq_add_cfqq_rr(struct cfq_data *cfqd, struct cfq_queue *cfqq)
{
	cfq_log_cfqq(cfqd, cfqq, "add_to_rr");
	BUG_ON(cfq_cfqq_on_rr(cfqq));
	cfq_mark_cfqq_on_rr(cfqq);
	cfqd->busy_queues++;
	if (cfq_cfqq_sync(cfqq))
		cfqd->busy_sync_queues++;

	cfq_resort_rr_list(cfqd, cfqq);
}

/*
 * Called when the cfqq no longer has requests pending, remove it from
 * the service tree.
 */
static void cfq_del_cfqq_rr(struct cfq_data *cfqd, struct cfq_queue *cfqq)
{
	cfq_log_cfqq(cfqd, cfqq, "del_from_rr");
	BUG_ON(!cfq_cfqq_on_rr(cfqq));
	cfq_clear_cfqq_on_rr(cfqq);

	if (!RB_EMPTY_NODE(&cfqq->rb_node)) {
		cfq_rb_erase(&cfqq->rb_node, cfqq->service_tree);
		cfqq->service_tree = NULL;
	}
	if (cfqq->p_root) {
		rb_erase(&cfqq->p_node, cfqq->p_root);
		cfqq->p_root = NULL;
	}

	cfq_group_notify_queue_del(cfqd, cfqq->cfqg);
	BUG_ON(!cfqd->busy_queues);
	cfqd->busy_queues--;
	if (cfq_cfqq_sync(cfqq))
		cfqd->busy_sync_queues--;
}

/*
 * rb tree support functions
 */
static void cfq_del_rq_rb(struct request *rq)
{
	struct cfq_queue *cfqq = RQ_CFQQ(rq);
	const int sync = rq_is_sync(rq);

	BUG_ON(!cfqq->queued[sync]);
	cfqq->queued[sync]--;

	elv_rb_del(&cfqq->sort_list, rq);

	if (cfq_cfqq_on_rr(cfqq) && RB_EMPTY_ROOT(&cfqq->sort_list)) {
		/*
		 * Queue will be deleted from service tree when we actually
		 * expire it later. Right now just remove it from prio tree
		 * as it is empty.
		 */
		if (cfqq->p_root) {
			rb_erase(&cfqq->p_node, cfqq->p_root);
			cfqq->p_root = NULL;
		}
	}
}

static void cfq_add_rq_rb(struct request *rq)
{
	struct cfq_queue *cfqq = RQ_CFQQ(rq);
	struct cfq_data *cfqd = cfqq->cfqd;
	struct request *prev;

	cfqq->queued[rq_is_sync(rq)]++;

	elv_rb_add(&cfqq->sort_list, rq);

	if (!cfq_cfqq_on_rr(cfqq))
		cfq_add_cfqq_rr(cfqd, cfqq);

	/*
	 * check if this request is a better next-serve candidate
	 */
	prev = cfqq->next_rq;
	cfqq->next_rq = cfq_choose_req(cfqd, cfqq->next_rq, rq, cfqd->last_position);

	/*
	 * adjust priority tree position, if ->next_rq changes
	 */
	if (prev != cfqq->next_rq)
		cfq_prio_tree_add(cfqd, cfqq);

	BUG_ON(!cfqq->next_rq);
}

static void cfq_reposition_rq_rb(struct cfq_queue *cfqq, struct request *rq)
{
	elv_rb_del(&cfqq->sort_list, rq);
	cfqq->queued[rq_is_sync(rq)]--;
	cfqg_stats_update_io_remove(RQ_CFQG(rq), rq->cmd_flags);
	cfq_add_rq_rb(rq);
	cfqg_stats_update_io_add(RQ_CFQG(rq), cfqq->cfqd->serving_group,
				 rq->cmd_flags);
}

static struct request *
cfq_find_rq_fmerge(struct cfq_data *cfqd, struct bio *bio)
{
	struct task_struct *tsk = current;
	struct cfq_io_cq *cic;
	struct cfq_queue *cfqq;

	cic = cfq_cic_lookup(cfqd, tsk->io_context);
	if (!cic)
		return NULL;

	cfqq = cic_to_cfqq(cic, cfq_bio_sync(bio));
	if (cfqq) {
		sector_t sector = bio->bi_sector + bio_sectors(bio);

		return elv_rb_find(&cfqq->sort_list, sector);
	}

	return NULL;
}

static void cfq_activate_request(struct request_queue *q, struct request *rq)
{
	struct cfq_data *cfqd = q->elevator->elevator_data;

	cfqd->rq_in_driver++;
	cfq_log_cfqq(cfqd, RQ_CFQQ(rq), "activate rq, drv=%d",
						cfqd->rq_in_driver);

	cfqd->last_position = blk_rq_pos(rq) + blk_rq_sectors(rq);
}

static void cfq_deactivate_request(struct request_queue *q, struct request *rq)
{
	struct cfq_data *cfqd = q->elevator->elevator_data;

	WARN_ON(!cfqd->rq_in_driver);
	cfqd->rq_in_driver--;
	cfq_log_cfqq(cfqd, RQ_CFQQ(rq), "deactivate rq, drv=%d",
						cfqd->rq_in_driver);
}

static void cfq_remove_request(struct request *rq)
{
	struct cfq_queue *cfqq = RQ_CFQQ(rq);

	if (cfqq->next_rq == rq)
		cfqq->next_rq = cfq_find_next_rq(cfqq->cfqd, cfqq, rq);

	list_del_init(&rq->queuelist);
	cfq_del_rq_rb(rq);

	cfqq->cfqd->rq_queued--;
	cfqg_stats_update_io_remove(RQ_CFQG(rq), rq->cmd_flags);
	if (rq->cmd_flags & REQ_PRIO) {
		WARN_ON(!cfqq->prio_pending);
		cfqq->prio_pending--;
	}
}

static int cfq_merge(struct request_queue *q, struct request **req,
		     struct bio *bio)
{
	struct cfq_data *cfqd = q->elevator->elevator_data;
	struct request *__rq;

	__rq = cfq_find_rq_fmerge(cfqd, bio);
	if (__rq && elv_rq_merge_ok(__rq, bio)) {
		*req = __rq;
		return ELEVATOR_FRONT_MERGE;
	}

	return ELEVATOR_NO_MERGE;
}

static void cfq_merged_request(struct request_queue *q, struct request *req,
			       int type)
{
	if (type == ELEVATOR_FRONT_MERGE) {
		struct cfq_queue *cfqq = RQ_CFQQ(req);

		cfq_reposition_rq_rb(cfqq, req);
	}
}

static void cfq_bio_merged(struct request_queue *q, struct request *req,
				struct bio *bio)
{
	cfqg_stats_update_io_merged(RQ_CFQG(req), bio->bi_rw);
}

static void
cfq_merged_requests(struct request_queue *q, struct request *rq,
		    struct request *next)
{
	struct cfq_queue *cfqq = RQ_CFQQ(rq);
	struct cfq_data *cfqd = q->elevator->elevator_data;

	/*
	 * reposition in fifo if next is older than rq
	 */
	if (!list_empty(&rq->queuelist) && !list_empty(&next->queuelist) &&
	    time_before(rq_fifo_time(next), rq_fifo_time(rq))) {
		list_move(&rq->queuelist, &next->queuelist);
		rq_set_fifo_time(rq, rq_fifo_time(next));
	}

	if (cfqq->next_rq == next)
		cfqq->next_rq = rq;
	cfq_remove_request(next);
	cfqg_stats_update_io_merged(RQ_CFQG(rq), next->cmd_flags);

	cfqq = RQ_CFQQ(next);
	/*
	 * all requests of this queue are merged to other queues, delete it
	 * from the service tree. If it's the active_queue,
	 * cfq_dispatch_requests() will choose to expire it or do idle
	 */
	if (cfq_cfqq_on_rr(cfqq) && RB_EMPTY_ROOT(&cfqq->sort_list) &&
	    cfqq != cfqd->active_queue)
		cfq_del_cfqq_rr(cfqd, cfqq);
}

static int cfq_allow_merge(struct request_queue *q, struct request *rq,
			   struct bio *bio)
{
	struct cfq_data *cfqd = q->elevator->elevator_data;
	struct cfq_io_cq *cic;
	struct cfq_queue *cfqq;

	/*
	 * Disallow merge of a sync bio into an async request.
	 */
	if (cfq_bio_sync(bio) && !rq_is_sync(rq))
		return false;

	/*
	 * Lookup the cfqq that this bio will be queued with and allow
	 * merge only if rq is queued there.
	 */
	cic = cfq_cic_lookup(cfqd, current->io_context);
	if (!cic)
		return false;

	cfqq = cic_to_cfqq(cic, cfq_bio_sync(bio));
	return cfqq == RQ_CFQQ(rq);
}

static inline void cfq_del_timer(struct cfq_data *cfqd, struct cfq_queue *cfqq)
{
	del_timer(&cfqd->idle_slice_timer);
	cfqg_stats_update_idle_time(cfqq->cfqg);
}

static void __cfq_set_active_queue(struct cfq_data *cfqd,
				   struct cfq_queue *cfqq)
{
	if (cfqq) {
		cfq_log_cfqq(cfqd, cfqq, "set_active wl_prio:%d wl_type:%d",
				cfqd->serving_prio, cfqd->serving_type);
		cfqg_stats_update_avg_queue_size(cfqq->cfqg);
		cfqq->slice_start = 0;
		cfqq->dispatch_start = jiffies;
		cfqq->allocated_slice = 0;
		cfqq->slice_end = 0;
		cfqq->slice_dispatch = 0;
		cfqq->nr_sectors = 0;

		cfq_clear_cfqq_wait_request(cfqq);
		cfq_clear_cfqq_must_dispatch(cfqq);
		cfq_clear_cfqq_must_alloc_slice(cfqq);
		cfq_clear_cfqq_fifo_expire(cfqq);
		cfq_mark_cfqq_slice_new(cfqq);

		cfq_del_timer(cfqd, cfqq);
	}

	cfqd->active_queue = cfqq;
}

/*
 * current cfqq expired its slice (or was too idle), select new one
 */
static void
__cfq_slice_expired(struct cfq_data *cfqd, struct cfq_queue *cfqq,
		    bool timed_out)
{
	cfq_log_cfqq(cfqd, cfqq, "slice expired t=%d", timed_out);

	if (cfq_cfqq_wait_request(cfqq))
		cfq_del_timer(cfqd, cfqq);

	cfq_clear_cfqq_wait_request(cfqq);
	cfq_clear_cfqq_wait_busy(cfqq);

	/*
	 * If this cfqq is shared between multiple processes, check to
	 * make sure that those processes are still issuing I/Os within
	 * the mean seek distance.  If not, it may be time to break the
	 * queues apart again.
	 */
	if (cfq_cfqq_coop(cfqq) && CFQQ_SEEKY(cfqq))
		cfq_mark_cfqq_split_coop(cfqq);

	/*
	 * store what was left of this slice, if the queue idled/timed out
	 */
	if (timed_out) {
		if (cfq_cfqq_slice_new(cfqq))
			cfqq->slice_resid = cfq_scaled_cfqq_slice(cfqd, cfqq);
		else
			cfqq->slice_resid = cfqq->slice_end - jiffies;
		cfq_log_cfqq(cfqd, cfqq, "resid=%ld", cfqq->slice_resid);
	}

	cfq_group_served(cfqd, cfqq->cfqg, cfqq);

	if (cfq_cfqq_on_rr(cfqq) && RB_EMPTY_ROOT(&cfqq->sort_list))
		cfq_del_cfqq_rr(cfqd, cfqq);

	cfq_resort_rr_list(cfqd, cfqq);

	if (cfqq == cfqd->active_queue)
		cfqd->active_queue = NULL;

	if (cfqd->active_cic) {
		put_io_context(cfqd->active_cic->icq.ioc);
		cfqd->active_cic = NULL;
	}
}

static inline void cfq_slice_expired(struct cfq_data *cfqd, bool timed_out)
{
	struct cfq_queue *cfqq = cfqd->active_queue;

	if (cfqq)
		__cfq_slice_expired(cfqd, cfqq, timed_out);
}

/*
 * Get next queue for service. Unless we have a queue preemption,
 * we'll simply select the first cfqq in the service tree.
 */
static struct cfq_queue *cfq_get_next_queue(struct cfq_data *cfqd)
{
	struct cfq_rb_root *service_tree =
		service_tree_for(cfqd->serving_group, cfqd->serving_prio,
					cfqd->serving_type);

	if (!cfqd->rq_queued)
		return NULL;

	/* There is nothing to dispatch */
	if (!service_tree)
		return NULL;
	if (RB_EMPTY_ROOT(&service_tree->rb))
		return NULL;
	return cfq_rb_first(service_tree);
}

static struct cfq_queue *cfq_get_next_queue_forced(struct cfq_data *cfqd)
{
	struct cfq_group *cfqg;
	struct cfq_queue *cfqq;
	int i, j;
	struct cfq_rb_root *st;

	if (!cfqd->rq_queued)
		return NULL;

	cfqg = cfq_get_next_cfqg(cfqd);
	if (!cfqg)
		return NULL;

	for_each_cfqg_st(cfqg, i, j, st)
		if ((cfqq = cfq_rb_first(st)) != NULL)
			return cfqq;
	return NULL;
}

/*
 * Get and set a new active queue for service.
 */
static struct cfq_queue *cfq_set_active_queue(struct cfq_data *cfqd,
					      struct cfq_queue *cfqq)
{
	if (!cfqq)
		cfqq = cfq_get_next_queue(cfqd);

	__cfq_set_active_queue(cfqd, cfqq);
	return cfqq;
}

static inline sector_t cfq_dist_from_last(struct cfq_data *cfqd,
					  struct request *rq)
{
	if (blk_rq_pos(rq) >= cfqd->last_position)
		return blk_rq_pos(rq) - cfqd->last_position;
	else
		return cfqd->last_position - blk_rq_pos(rq);
}

static inline int cfq_rq_close(struct cfq_data *cfqd, struct cfq_queue *cfqq,
			       struct request *rq)
{
	return cfq_dist_from_last(cfqd, rq) <= CFQQ_CLOSE_THR;
}

static bool cfq_should_idle(struct cfq_data *cfqd, struct cfq_queue *cfqq)
{
	enum wl_prio_t prio = cfqq_prio(cfqq);
	struct cfq_rb_root *service_tree = cfqq->service_tree;

	BUG_ON(!service_tree);
	BUG_ON(!service_tree->count);

	if (!cfqd->cfq_slice_idle)
		return false;

	/* We never do for idle class queues. */
	if (prio == IDLE_WORKLOAD)
		return false;

	/* We do for queues that were marked with idle window flag. */
	if (cfq_cfqq_idle_window(cfqq) &&
	   !(blk_queue_nonrot(cfqd->queue) && cfqd->hw_tag))
		return true;

	/*
	 * Otherwise, we do only if they are the last ones
	 * in their service tree.
	 */
	if (service_tree->count == 1 && cfq_cfqq_sync(cfqq) &&
	   !cfq_io_thinktime_big(cfqd, &service_tree->ttime, false))
		return true;
	cfq_log_cfqq(cfqd, cfqq, "Not idling. st->count:%d",
			service_tree->count);
	return false;
}

static void cfq_arm_slice_timer(struct cfq_data *cfqd)
{
	struct cfq_queue *cfqq = cfqd->active_queue;
	struct cfq_io_cq *cic;
	unsigned long sl, group_idle = 0;

	/*
	 * SSD device without seek penalty, disable idling. But only do so
	 * for devices that support queuing, otherwise we still have a problem
	 * with sync vs async workloads.
	 */
	if (blk_queue_nonrot(cfqd->queue) && cfqd->hw_tag)
		return;

	WARN_ON(!RB_EMPTY_ROOT(&cfqq->sort_list));
	WARN_ON(cfq_cfqq_slice_new(cfqq));

	/*
	 * idle is disabled, either manually or by past process history
	 */
	if (!cfq_should_idle(cfqd, cfqq)) {
		/* no queue idling. Check for group idling */
		if (cfqd->cfq_group_idle)
			group_idle = cfqd->cfq_group_idle;
		else
			return;
	}

	/*
	 * still active requests from this queue, don't idle
	 */
	if (cfqq->dispatched)
		return;

	/*
	 * task has exited, don't wait
	 */
	cic = cfqd->active_cic;
	if (!cic || !atomic_read(&cic->icq.ioc->active_ref))
		return;

	/*
	 * If our average think time is larger than the remaining time
	 * slice, then don't idle. This avoids overrunning the allotted
	 * time slice.
	 */
	if (sample_valid(cic->ttime.ttime_samples) &&
	    (cfqq->slice_end - jiffies < cic->ttime.ttime_mean)) {
		cfq_log_cfqq(cfqd, cfqq, "Not idling. think_time:%lu",
			     cic->ttime.ttime_mean);
		return;
	}

	/* There are other queues in the group, don't do group idle */
	if (group_idle && cfqq->cfqg->nr_cfqq > 1)
		return;

	cfq_mark_cfqq_wait_request(cfqq);

	if (group_idle)
		sl = cfqd->cfq_group_idle;
	else
		sl = cfqd->cfq_slice_idle;

	mod_timer(&cfqd->idle_slice_timer, jiffies + sl);
	cfqg_stats_set_start_idle_time(cfqq->cfqg);
	cfq_log_cfqq(cfqd, cfqq, "arm_idle: %lu group_idle: %d", sl,
			group_idle ? 1 : 0);
}

/*
 * Move request from internal lists to the request queue dispatch list.
 */
static void cfq_dispatch_insert(struct request_queue *q, struct request *rq)
{
	struct cfq_data *cfqd = q->elevator->elevator_data;
	struct cfq_queue *cfqq = RQ_CFQQ(rq);

	cfq_log_cfqq(cfqd, cfqq, "dispatch_insert");

	cfqq->next_rq = cfq_find_next_rq(cfqd, cfqq, rq);
	cfq_remove_request(rq);
	cfqq->dispatched++;
	(RQ_CFQG(rq))->dispatched++;
	elv_dispatch_sort(q, rq);

	cfqd->rq_in_flight[cfq_cfqq_sync(cfqq)]++;
	cfqq->nr_sectors += blk_rq_sectors(rq);
	cfqg_stats_update_dispatch(cfqq->cfqg, blk_rq_bytes(rq), rq->cmd_flags);
}

/*
 * return expired entry, or NULL to just start from scratch in rbtree
 */
static struct request *cfq_check_fifo(struct cfq_queue *cfqq)
{
	struct request *rq = NULL;

	if (cfq_cfqq_fifo_expire(cfqq))
		return NULL;

	cfq_mark_cfqq_fifo_expire(cfqq);

	if (list_empty(&cfqq->fifo))
		return NULL;

	rq = rq_entry_fifo(cfqq->fifo.next);
	if (time_before(jiffies, rq_fifo_time(rq)))
		rq = NULL;

	cfq_log_cfqq(cfqq->cfqd, cfqq, "fifo=%p", rq);
	return rq;
}

static inline int
cfq_prio_to_maxrq(struct cfq_data *cfqd, struct cfq_queue *cfqq)
{
	const int base_rq = cfqd->cfq_slice_async_rq;

	WARN_ON(cfqq->ioprio >= IOPRIO_BE_NR);

	return 2 * base_rq * (IOPRIO_BE_NR - cfqq->ioprio);
}

/*
 * Must be called with the queue_lock held.
 */
static int cfqq_process_refs(struct cfq_queue *cfqq)
{
	int process_refs, io_refs;

	io_refs = cfqq->allocated[READ] + cfqq->allocated[WRITE];
	process_refs = cfqq->ref - io_refs;
	BUG_ON(process_refs < 0);
	return process_refs;
}

static void cfq_setup_merge(struct cfq_queue *cfqq, struct cfq_queue *new_cfqq)
{
	int process_refs, new_process_refs;
	struct cfq_queue *__cfqq;

	/*
	 * If there are no process references on the new_cfqq, then it is
	 * unsafe to follow the ->new_cfqq chain as other cfqq's in the
	 * chain may have dropped their last reference (not just their
	 * last process reference).
	 */
	if (!cfqq_process_refs(new_cfqq))
		return;

	/* Avoid a circular list and skip interim queue merges */
	while ((__cfqq = new_cfqq->new_cfqq)) {
		if (__cfqq == cfqq)
			return;
		new_cfqq = __cfqq;
	}

	process_refs = cfqq_process_refs(cfqq);
	new_process_refs = cfqq_process_refs(new_cfqq);
	/*
	 * If the process for the cfqq has gone away, there is no
	 * sense in merging the queues.
	 */
	if (process_refs == 0 || new_process_refs == 0)
		return;

	/*
	 * Merge in the direction of the lesser amount of work.
	 */
	if (new_process_refs >= process_refs) {
		cfqq->new_cfqq = new_cfqq;
		new_cfqq->ref += process_refs;
	} else {
		new_cfqq->new_cfqq = cfqq;
		cfqq->ref += new_process_refs;
	}
}

static inline bool cfq_slice_used_soon(struct cfq_data *cfqd,
	struct cfq_queue *cfqq)
{
	/* the queue hasn't finished any request, can't estimate */
	if (cfq_cfqq_slice_new(cfqq))
		return true;
	if (time_after(jiffies + cfqd->cfq_slice_idle * cfqq->dispatched,
		cfqq->slice_end))
		return true;

	return false;
}



#ifdef CFQ_GROUP_IOSCHED
static void check_blkcg_changed(struct cfq_io_cq *cic, struct bio *bio)
{
	struct cfq_data *cfqd = cic_to_cfqd(cic);
	struct cfq_queue *sync_cfqq;
	uint64_t id;

	rcu_read_lock();
	id = bio_blkio_cgroup(bio)->id;
	rcu_read_unlock();

	/*
	 * Check whether blkcg has changed.  The condition may trigger
	 * spuriously on a newly created cic but there's no harm.
	 */
	if (unlikely(!cfqd) || likely(cic->blkcg_id == id))
		return;

	sync_cfqq = cic_to_cfqq(cic, 1);
	if (sync_cfqq) {
		/*
		 * Drop reference to sync queue. A new sync queue will be
		 * assigned in new group upon arrival of a fresh request.
		 */
		cfq_log_cfqq(cfqd, sync_cfqq, "changed cgroup");
		cic_set_cfqq(cic, NULL, 1);
		cfq_put_queue(sync_cfqq);
	}

	cic->blkcg_id = id;
}
#else
static inline void check_blkcg_changed(struct cfq_io_cq *cic, struct bio *bio) { }
#endif  /* CFQ_GROUP_IOSCHED */

static struct cfq_queue *
cfq_find_alloc_queue(struct cfq_data *cfqd, bool is_sync, struct cfq_io_cq *cic,
		     struct bio *bio, gfp_t gfp_mask)
{
	struct blkio_cgroup *blkcg;
	struct cfq_queue *cfqq, *new_cfqq = NULL;
	struct cfq_group *cfqg;

retry:
	rcu_read_lock();

	blkcg = bio_blkio_cgroup(bio);
	cfqg = cfq_lookup_create_cfqg(cfqd, blkcg);
	cfqq = cic_to_cfqq(cic, is_sync);

	/*
	 * Always try a new alloc if we fell back to the OOM cfqq
	 * originally, since it should just be a temporary situation.
	 */
	if (!cfqq || cfqq == &cfqd->oom_cfqq) {
		cfqq = NULL;
		if (new_cfqq) {
			cfqq = new_cfqq;
			new_cfqq = NULL;
		} else if (gfp_mask & __GFP_WAIT) {
			rcu_read_unlock();
			spin_unlock_irq(cfqd->queue->queue_lock);
			new_cfqq = kmem_cache_alloc_node(cfq_pool,
					gfp_mask | __GFP_ZERO,
					cfqd->queue->node);
			spin_lock_irq(cfqd->queue->queue_lock);
			if (new_cfqq)
				goto retry;
		} else {
			cfqq = kmem_cache_alloc_node(cfq_pool,
					gfp_mask | __GFP_ZERO,
					cfqd->queue->node);
		}

		if (cfqq) {
			cfq_init_cfqq(cfqd, cfqq, current->pid, is_sync);
			cfq_init_prio_data(cfqq, cic);
			cfq_link_cfqq_cfqg(cfqq, cfqg);
			cfq_log_cfqq(cfqd, cfqq, "alloced");
		} else
			cfqq = &cfqd->oom_cfqq;
	}

	if (new_cfqq)
		kmem_cache_free(cfq_pool, new_cfqq);

	rcu_read_unlock();
	return cfqq;
}

static struct cfq_queue **
cfq_async_queue_prio(struct cfq_data *cfqd, int ioprio_class, int ioprio)
{
	switch (ioprio_class) {
	case IOPRIO_CLASS_RT:
		return &cfqd->async_cfqq[0][ioprio];
	case IOPRIO_CLASS_NONE:
		ioprio = IOPRIO_NORM;
		/* fall through */
	case IOPRIO_CLASS_BE:
		return &cfqd->async_cfqq[1][ioprio];
	case IOPRIO_CLASS_IDLE:
		return &cfqd->async_idle_cfqq;
	default:
		BUG();
	}
}

static struct cfq_queue *
cfq_get_queue(struct cfq_data *cfqd, bool is_sync, struct cfq_io_cq *cic,
	      struct bio *bio, gfp_t gfp_mask)
{
	const int ioprio_class = IOPRIO_PRIO_CLASS(cic->ioprio);
	const int ioprio = IOPRIO_PRIO_DATA(cic->ioprio);
	struct cfq_queue **async_cfqq = NULL;
	struct cfq_queue *cfqq = NULL;

	if (!is_sync) {
		async_cfqq = cfq_async_queue_prio(cfqd, ioprio_class, ioprio);
		cfqq = *async_cfqq;
	}

	if (!cfqq)
		cfqq = cfq_find_alloc_queue(cfqd, is_sync, cic, bio, gfp_mask);

	/*
	 * pin the queue now that it's allocated, scheduler exit will prune it
	 */
	if (!is_sync && !(*async_cfqq)) {
		cfqq->ref++;
		*async_cfqq = cfqq;
	}

	cfqq->ref++;
	return cfqq;
}

static void
__cfq_update_io_thinktime(struct cfq_ttime *ttime, unsigned long slice_idle)
{
	unsigned long elapsed = jiffies - ttime->last_end_request;
	elapsed = min(elapsed, 2UL * slice_idle);

	ttime->ttime_samples = (7*ttime->ttime_samples + 256) / 8;
	ttime->ttime_total = (7*ttime->ttime_total + 256*elapsed) / 8;
	ttime->ttime_mean = (ttime->ttime_total + 128) / ttime->ttime_samples;
}

static void
cfq_update_io_thinktime(struct cfq_data *cfqd, struct cfq_queue *cfqq,
			struct cfq_io_cq *cic)
{
	if (cfq_cfqq_sync(cfqq)) {
		__cfq_update_io_thinktime(&cic->ttime, cfqd->cfq_slice_idle);
		__cfq_update_io_thinktime(&cfqq->service_tree->ttime,
			cfqd->cfq_slice_idle);
	}
#ifdef CFQ_GROUP_IOSCHED
	__cfq_update_io_thinktime(&cfqq->cfqg->ttime, cfqd->cfq_group_idle);
#endif
}

static void
cfq_update_io_seektime(struct cfq_data *cfqd, struct cfq_queue *cfqq,
		       struct request *rq)
{
	sector_t sdist = 0;
	sector_t n_sec = blk_rq_sectors(rq);
	if (cfqq->last_request_pos) {
		if (cfqq->last_request_pos < blk_rq_pos(rq))
			sdist = blk_rq_pos(rq) - cfqq->last_request_pos;
		else
			sdist = cfqq->last_request_pos - blk_rq_pos(rq);
	}

	cfqq->seek_history <<= 1;
	if (blk_queue_nonrot(cfqd->queue))
		cfqq->seek_history |= (n_sec < CFQQ_SECT_THR_NONROT);
	else
		cfqq->seek_history |= (sdist > CFQQ_SEEK_THR);
}

/*
 * Disable idle window if the process thinks too long or seeks so much that
 * it doesn't matter
 */
static void
cfq_update_idle_window(struct cfq_data *cfqd, struct cfq_queue *cfqq,
		       struct cfq_io_cq *cic)
{
	int old_idle, enable_idle;

	/*
	 * Don't idle for async or idle io prio class
	 */
	if (!cfq_cfqq_sync(cfqq) || cfq_class_idle(cfqq))
		return;

	enable_idle = old_idle = cfq_cfqq_idle_window(cfqq);

	if (cfqq->queued[0] + cfqq->queued[1] >= 4)
		cfq_mark_cfqq_deep(cfqq);

	if (cfqq->next_rq && (cfqq->next_rq->cmd_flags & REQ_NOIDLE))
		enable_idle = 0;
	else if (!atomic_read(&cic->icq.ioc->active_ref) ||
		 !cfqd->cfq_slice_idle ||
		 (!cfq_cfqq_deep(cfqq) && CFQQ_SEEKY(cfqq)))
		enable_idle = 0;
	else if (sample_valid(cic->ttime.ttime_samples)) {
		if (cic->ttime.ttime_mean > cfqd->cfq_slice_idle)
			enable_idle = 0;
		else
			enable_idle = 1;
	}

	if (old_idle != enable_idle) {
		cfq_log_cfqq(cfqd, cfqq, "idle=%d", enable_idle);
		if (enable_idle)
			cfq_mark_cfqq_idle_window(cfqq);
		else
			cfq_clear_cfqq_idle_window(cfqq);
	}
}

/*
 * Check if new_cfqq should preempt the currently active queue. Return 0 for
 * no or if we aren't sure, a 1 will cause a preempt.
 */
static bool
cfq_should_preempt(struct cfq_data *cfqd, struct cfq_queue *new_cfqq,
		   struct request *rq)
{
	struct cfq_queue *cfqq;

	cfqq = cfqd->active_queue;
	if (!cfqq)
		return false;

	if (cfq_class_idle(new_cfqq))
		return false;

	if (cfq_class_idle(cfqq))
		return true;

	/*
	 * Don't allow a non-RT request to preempt an ongoing RT cfqq timeslice.
	 */
	if (cfq_class_rt(cfqq) && !cfq_class_rt(new_cfqq))
		return false;

	/*
	 * if the new request is sync, but the currently running queue is
	 * not, let the sync request have priority.
	 */
	if (rq_is_sync(rq) && !cfq_cfqq_sync(cfqq))
		return true;

	if (new_cfqq->cfqg != cfqq->cfqg)
		return false;

	if (cfq_slice_used(cfqq))
		return true;

	/* Allow preemption only if we are idling on sync-noidle tree */
	if (cfqd->serving_type == SYNC_NOIDLE_WORKLOAD &&
	    cfqq_type(new_cfqq) == SYNC_NOIDLE_WORKLOAD &&
	    new_cfqq->service_tree->count == 2 &&
	    RB_EMPTY_ROOT(&cfqq->sort_list))
		return true;

	/*
	 * So both queues are sync. Let the new request get disk time if
	 * it's a metadata request and the current queue is doing regular IO.
	 */
	if ((rq->cmd_flags & REQ_PRIO) && !cfqq->prio_pending)
		return true;

	/*
	 * Allow an RT request to pre-empt an ongoing non-RT cfqq timeslice.
	 */
	if (cfq_class_rt(new_cfqq) && !cfq_class_rt(cfqq))
		return true;

	/* An idle queue should not be idle now for some reason */
	if (RB_EMPTY_ROOT(&cfqq->sort_list) && !cfq_should_idle(cfqd, cfqq))
		return true;

	if (!cfqd->active_cic || !cfq_cfqq_wait_request(cfqq))
		return false;

	/*
	 * if this request is as-good as one we would expect from the
	 * current cfqq, let it preempt
	 */
	if (cfq_rq_close(cfqd, cfqq, rq))
		return true;

	return false;
}

/*
 * cfqq preempts the active queue. if we allowed preempt with no slice left,
 * let it have half of its nominal slice.
 */
static void cfq_preempt_queue(struct cfq_data *cfqd, struct cfq_queue *cfqq)
{
	enum wl_type_t old_type = cfqq_type(cfqd->active_queue);

	cfq_log_cfqq(cfqd, cfqq, "preempt");
	cfq_slice_expired(cfqd, 1);

	/*
	 * workload type is changed, don't save slice, otherwise preempt
	 * doesn't happen
	 */
	if (old_type != cfqq_type(cfqq))
		cfqq->cfqg->saved_workload_slice = 0;

	/*
	 * Put the new queue at the front of the of the current list,
	 * so we know that it will be selected next.
	 */
	BUG_ON(!cfq_cfqq_on_rr(cfqq));

	cfq_service_tree_add(cfqd, cfqq, 1);

	cfqq->slice_end = 0;
	cfq_mark_cfqq_slice_new(cfqq);
}

/*
 * Called when a new fs request (rq) is added (to cfqq). Check if there's
 * something we should do about it
 */
static void
cfq_rq_enqueued(struct cfq_data *cfqd, struct cfq_queue *cfqq,
		struct request *rq)
{
	struct cfq_io_cq *cic = RQ_CIC(rq);

	cfqd->rq_queued++;
	if (rq->cmd_flags & REQ_PRIO)
		cfqq->prio_pending++;

	cfq_update_io_thinktime(cfqd, cfqq, cic);
	cfq_update_io_seektime(cfqd, cfqq, rq);
	cfq_update_idle_window(cfqd, cfqq, cic);

	cfqq->last_request_pos = blk_rq_pos(rq) + blk_rq_sectors(rq);

	if (cfqq == cfqd->active_queue) {
		/*
		 * Remember that we saw a request from this process, but
		 * don't start queuing just yet. Otherwise we risk seeing lots
		 * of tiny requests, because we disrupt the normal plugging
		 * and merging. If the request is already larger than a single
		 * page, let it rip immediately. For that case we assume that
		 * merging is already done. Ditto for a busy system that
		 * has other work pending, don't risk delaying until the
		 * idle timer unplug to continue working.
		 */
		if (cfq_cfqq_wait_request(cfqq)) {
			if (blk_rq_bytes(rq) > PAGE_CACHE_SIZE ||
			    cfqd->busy_queues > 1) {
				cfq_del_timer(cfqd, cfqq);
				cfq_clear_cfqq_wait_request(cfqq);
				__blk_run_queue(cfqd->queue);
			} else {
				cfqg_stats_update_idle_time(cfqq->cfqg);
				cfq_mark_cfqq_must_dispatch(cfqq);
			}
		}
	} else if (cfq_should_preempt(cfqd, cfqq, rq)) {
		/*
		 * not the active queue - expire current slice if it is
		 * idle and has expired it's mean thinktime or this new queue
		 * has some old slice time left and is of higher priority or
		 * this new queue is RT and the current one is BE
		 */
		cfq_preempt_queue(cfqd, cfqq);
		__blk_run_queue(cfqd->queue);
	}
}

static void cfq_insert_request(struct request_queue *q, struct request *rq)
{
	struct cfq_data *cfqd = q->elevator->elevator_data;
	struct cfq_queue *cfqq = RQ_CFQQ(rq);

	cfq_log_cfqq(cfqd, cfqq, "insert_request");
	cfq_init_prio_data(cfqq, RQ_CIC(rq));

	rq_set_fifo_time(rq, jiffies + cfqd->cfq_fifo_expire[rq_is_sync(rq)]);
	list_add_tail(&rq->queuelist, &cfqq->fifo);
	cfq_add_rq_rb(rq);
	cfqg_stats_update_io_add(RQ_CFQG(rq), cfqd->serving_group,
				 rq->cmd_flags);
	cfq_rq_enqueued(cfqd, cfqq, rq);
}

/*
 * Update hw_tag based on peak queue depth over 50 samples under
 * sufficient load.
 */
static void cfq_update_hw_tag(struct cfq_data *cfqd)
{
	struct cfq_queue *cfqq = cfqd->active_queue;

	if (cfqd->rq_in_driver > cfqd->hw_tag_est_depth)
		cfqd->hw_tag_est_depth = cfqd->rq_in_driver;

	if (cfqd->hw_tag == 1)
		return;

	if (cfqd->rq_queued <= CFQ_HW_QUEUE_MIN &&
	    cfqd->rq_in_driver <= CFQ_HW_QUEUE_MIN)
		return;

	/*
	 * If active queue hasn't enough requests and can idle, cfq might not
	 * dispatch sufficient requests to hardware. Don't zero hw_tag in this
	 * case
	 */
	if (cfqq && cfq_cfqq_idle_window(cfqq) &&
	    cfqq->dispatched + cfqq->queued[0] + cfqq->queued[1] <
	    CFQ_HW_QUEUE_MIN && cfqd->rq_in_driver < CFQ_HW_QUEUE_MIN)
		return;

	if (cfqd->hw_tag_samples++ < 50)
		return;

	if (cfqd->hw_tag_est_depth >= CFQ_HW_QUEUE_MIN)
		cfqd->hw_tag = 1;
	else
		cfqd->hw_tag = 0;
}

static bool cfq_should_wait_busy(struct cfq_data *cfqd, struct cfq_queue *cfqq)
{
	struct cfq_io_cq *cic = cfqd->active_cic;

	/* If the queue already has requests, don't wait */
	if (!RB_EMPTY_ROOT(&cfqq->sort_list))
		return false;

	/* If there are other queues in the group, don't wait */
	if (cfqq->cfqg->nr_cfqq > 1)
		return false;

	/* the only queue in the group, but think time is big */
	if (cfq_io_thinktime_big(cfqd, &cfqq->cfqg->ttime, true))
		return false;

	if (cfq_slice_used(cfqq))
		return true;

	/* if slice left is less than think time, wait busy */
	if (cic && sample_valid(cic->ttime.ttime_samples)
	    && (cfqq->slice_end - jiffies < cic->ttime.ttime_mean))
		return true;

	/*
	 * If think times is less than a jiffy than ttime_mean=0 and above
	 * will not be true. It might happen that slice has not expired yet
	 * but will expire soon (4-5 ns) during select_queue(). To cover the
	 * case where think time is less than a jiffy, mark the queue wait
	 * busy if only 1 jiffy is left in the slice.
	 */
	if (cfqq->slice_end - jiffies == 1)
		return true;

	return false;
}

static void cfq_completed_request(struct request_queue *q, struct request *rq)
{
	struct cfq_queue *cfqq = RQ_CFQQ(rq);
	struct cfq_data *cfqd = cfqq->cfqd;
	const int sync = rq_is_sync(rq);
	unsigned long now;

	now = jiffies;
	cfq_log_cfqq(cfqd, cfqq, "complete rqnoidle %d",
		     !!(rq->cmd_flags & REQ_NOIDLE));

	cfq_update_hw_tag(cfqd);

	WARN_ON(!cfqd->rq_in_driver);
	WARN_ON(!cfqq->dispatched);
	cfqd->rq_in_driver--;
	cfqq->dispatched--;
	(RQ_CFQG(rq))->dispatched--;
	cfqg_stats_update_completion(cfqq->cfqg, rq_start_time_ns(rq),
				     rq_io_start_time_ns(rq), rq->cmd_flags);

	cfqd->rq_in_flight[cfq_cfqq_sync(cfqq)]--;

	if (sync) {
		struct cfq_rb_root *service_tree;

		RQ_CIC(rq)->ttime.last_end_request = now;

		if (cfq_cfqq_on_rr(cfqq))
			service_tree = cfqq->service_tree;
		else
			service_tree = service_tree_for(cfqq->cfqg,
				cfqq_prio(cfqq), cfqq_type(cfqq));
		service_tree->ttime.last_end_request = now;
		if (!time_after(rq->start_time + cfqd->cfq_fifo_expire[1], now))
			cfqd->last_delayed_sync = now;
	}

#ifdef CFQ_GROUP_IOSCHED
	cfqq->cfqg->ttime.last_end_request = now;
#endif

	/*
	 * If this is the active queue, check if it needs to be expired,
	 * or if we want to idle in case it has no pending requests.
	 */
	if (cfqd->active_queue == cfqq) {
		const bool cfqq_empty = RB_EMPTY_ROOT(&cfqq->sort_list);

		if (cfq_cfqq_slice_new(cfqq)) {
			cfq_set_prio_slice(cfqd, cfqq);
			cfq_clear_cfqq_slice_new(cfqq);
		}

		/*
		 * Should we wait for next request to come in before we expire
		 * the queue.
		 */
		if (cfq_should_wait_busy(cfqd, cfqq)) {
			unsigned long extend_sl = cfqd->cfq_slice_idle;
			if (!cfqd->cfq_slice_idle)
				extend_sl = cfqd->cfq_group_idle;
			cfqq->slice_end = jiffies + extend_sl;
			cfq_mark_cfqq_wait_busy(cfqq);
			cfq_log_cfqq(cfqd, cfqq, "will busy wait");
		}

		/*
		 * Idling is not enabled on:
		 * - expired queues
		 * - idle-priority queues
		 * - async queues
		 * - queues with still some requests queued
		 * - when there is a close cooperator
		 */
		if (cfq_slice_used(cfqq) || cfq_class_idle(cfqq))
			cfq_slice_expired(cfqd, 1);
		else if (sync && cfqq_empty &&
			 !cfq_close_cooperator(cfqd, cfqq)) {
			cfq_arm_slice_timer(cfqd);
		}
	}

	if (!cfqd->rq_in_driver)
		cfq_schedule_dispatch(cfqd);
}

static inline int __cfq_may_queue(struct cfq_queue *cfqq)
{
	if (cfq_cfqq_wait_request(cfqq) && !cfq_cfqq_must_alloc_slice(cfqq)) {
		cfq_mark_cfqq_must_alloc_slice(cfqq);
		return ELV_MQUEUE_MUST;
	}

	return ELV_MQUEUE_MAY;
}

static int cfq_may_queue(struct request_queue *q, int rw)
{
	struct cfq_data *cfqd = q->elevator->elevator_data;
	struct task_struct *tsk = current;
	struct cfq_io_cq *cic;
	struct cfq_queue *cfqq;

	/*
	 * don't force setup of a queue from here, as a call to may_queue
	 * does not necessarily imply that a request actually will be queued.
	 * so just lookup a possibly existing queue, or return 'may queue'
	 * if that fails
	 */
	cic = cfq_cic_lookup(cfqd, tsk->io_context);
	if (!cic)
		return ELV_MQUEUE_MAY;

	cfqq = cic_to_cfqq(cic, rw_is_sync(rw));
	if (cfqq) {
		cfq_init_prio_data(cfqq, cic);

		return __cfq_may_queue(cfqq);
	}

	return ELV_MQUEUE_MAY;
}

/*
 * queue lock held here
 */
static void cfq_put_request(struct request *rq)
{
	struct cfq_queue *cfqq = RQ_CFQQ(rq);

	if (cfqq) {
		const int rw = rq_data_dir(rq);

		BUG_ON(!cfqq->allocated[rw]);
		cfqq->allocated[rw]--;

		/* Put down rq reference on cfqg */
		cfqg_put(RQ_CFQG(rq));
		rq->elv.priv[0] = NULL;
		rq->elv.priv[1] = NULL;

		cfq_put_queue(cfqq);
	}
}

static struct cfq_queue *
cfq_merge_cfqqs(struct cfq_data *cfqd, struct cfq_io_cq *cic,
		struct cfq_queue *cfqq)
{
	cfq_log_cfqq(cfqd, cfqq, "merging with queue %p", cfqq->new_cfqq);
	cic_set_cfqq(cic, cfqq->new_cfqq, 1);
	cfq_mark_cfqq_coop(cfqq->new_cfqq);
	cfq_put_queue(cfqq);
	return cic_to_cfqq(cic, 1);
}

/*
 * Returns NULL if a new cfqq should be allocated, or the old cfqq if this
 * was the last process referring to said cfqq.
 */
static struct cfq_queue *
split_cfqq(struct cfq_io_cq *cic, struct cfq_queue *cfqq)
{
	if (cfqq_process_refs(cfqq) == 1) {
		cfqq->pid = current->pid;
		cfq_clear_cfqq_coop(cfqq);
		cfq_clear_cfqq_split_coop(cfqq);
		return cfqq;
	}

	cic_set_cfqq(cic, NULL, 1);

	cfq_put_cooperator(cfqq);

	cfq_put_queue(cfqq);
	return NULL;
}


static void cfq_exit_queue(struct elevator_queue *e)
{
	struct cfq_data *cfqd = e->elevator_data;
	struct request_queue *q = cfqd->queue;


#ifndef CFQ_GROUP_IOSCHED
	kfree(cfqd->root_group);
#endif
	update_root_blkg_pd(q, BLKIO_POLICY_PROP);
	kfree(cfqd);
}

static int cfq_init_queue(struct request_queue *q)
{
	struct cfq_data *cfqd;
	struct blkio_group *blkg ;
	int i;


	/* Init root group and prefer root group over other groups by default */
#ifdef CFQ_GROUP_IOSCHED
	rcu_read_lock();
#else
	cfqd->root_group = kzalloc_node();
#endif
	if (!cfqd->root_group) {
		kfree(cfqd);
		return -ENOMEM;
	}

	
	return 0;
}


#ifdef CFQ_GROUP_IOSCHED
int x;
#endif

static int cfq_init(void)
{
	int ret;

	/*
	 * could be 0 on HZ < 1000 setups
	 */
	if (!cfq_slice_async)
		cfq_slice_async = 1;
	if (!cfq_slice_idle)
		cfq_slice_idle = 1;

#ifdef CFQ_GROUP_IOSCHED
	if (!cfq_group_idle)
		cfq_group_idle = 1;
#else
		cfq_group_idle = 0;
#endif
	cfq_pool = KMEM_CACHE(cfq_queue, 0);
	if (!cfq_pool)
		return -ENOMEM;

	ret = elv_register(&iosched_cfq);
	if (ret) {
		kmem_cache_destroy(cfq_pool);
		return ret;
	}

#ifdef CFQ_GROUP_IOSCHED
	blkio_policy_register(&blkio_policy_cfq);
#endif
	return 0;
}

static void cfq_exit(void)
{
#ifdef CFQ_GROUP_IOSCHED
	blkio_policy_unregister(&blkio_policy_cfq);
#endif
	elv_unregister(&iosched_cfq);
	kmem_cache_destroy(cfq_pool);
}

