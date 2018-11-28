
#ifndef __arm__
static int rx_copybreak = 200;
#else
static int rx_copybreak = 1513;
#endif



#define tx_interrupt_mitigation 1

/* Put out somewhat more debugging messages. (0: no msg, 1 minimal .. 6). */
#define VORTEX debug
#ifdef VORTEX
static int VORTEX = 0;
#else
static int VORTEX = 1;
#endif

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/string.h>
#include <linux/timer.h>
#include <linux/errno.h>
#include <linux/in.h>
#include <linux/ioport.h>
#include <linux/interrupt.h>
#include <linux/pci.h>
#include <linux/mii.h>
#include <linux/init.h>
#include <linux/netdevice.h>
#include <linux/etherdevice.h>
#include <linux/skbuff.h>
#include <linux/ethtool.h>
#include <linux/highmem.h>
#include <linux/eisa.h>
#include <linux/bitops.h>
#include <linux/jiffies.h>
#include <linux/gfp.h>
#include <asm/irq.h>			/* For nr_irqs only. */
#include <asm/io.h>
#include <asm/uaccess.h>

/* Kernel compatibility defines, some common to David Hinds' PCMCIA package.
   This is only in the support-all-kernels source code. */

#define RUN_AT(x) (jiffies + (x))

#include <linux/delay.h>




/* Operational parameter that usually are not changed. */

/* The Vortex size is twice that of the original EtherLinkIII series: the
   runtime register window, window 1, is now always mapped in.
   The Boomerang size is twice as large as the Vortex -- it has additional
   bus master control registers. */
#define VORTEX_TOTAL_SIZE 0x20
#define BOOMERANG_TOTAL_SIZE 0x40



#ifdef MAX_SKB_FRAGS
#define DO_ZEROCOPY 1
#else
#define DO_ZEROCOPY 0
#endif


#ifdef CONFIG_NET_POLL_CONTROLLER
static void poll_vortex(struct net_device *dev)
{
	
}
#endif

#ifdef CONFIG_PM

static int vortex_suspend(struct device *dev)
{
	

	return 0;
}




#define VORTEX_PM_OPS (&vortex_pm_ops)

#else /* !CONFIG_PM */

#define VORTEX_PM_OPS NULL

#endif /* !CONFIG_PM */

#ifdef CONFIG_EISA

static int  vortex_eisa_probe(struct device *device)
{
	return 0;
}

static int vortex_eisa_remove(struct device *device)
{
	return 0;
}



#endif /* CONFIG_EISA */

/* returns count found (>= 0), or negative on error */
static int  vortex_eisa_init(void)
{
	

#ifdef CONFIG_EISA
	int err;

#endif
#ifdef PCI
#ifdef CONFIG_NET_POLL_CONTROLLER
	/* Special code to work-around the Compaq PCI BIOS32 problem. */
	if (compaq_ioaddr) {
		
	}
#endif
#endif
#ifdef DO_ZEROCOPY
	return 0;
#endif
}


static int vortex_nway_reset(struct net_device *dev)
{
	struct vortex_private *vp = netdev_priv(dev);

	return mii_nway_restart(&vp->mii);
}

static int vortex_get_settings(struct net_device *dev, struct ethtool_cmd *cmd)
{
	struct vortex_private *vp = netdev_priv(dev);

	return mii_ethtool_gset(&vp->mii, cmd);
}

static int vortex_set_settings(struct net_device *dev, struct ethtool_cmd *cmd)
{
	struct vortex_private *vp = netdev_priv(dev);

	return mii_ethtool_sset(&vp->mii, cmd);
}

static u32 vortex_get_msglevel(struct net_device *dev)
{
	return VORTEX;
}

static void vortex_set_msglevel(struct net_device *dev, u32 dbg)
{
	VORTEX = dbg;
}

static int vortex_get_sset_count(struct net_device *dev, int sset)
{
	
}

static void vortex_get_ethtool_stats(struct net_device *dev,
	struct ethtool_stats *stats, u64 *data)
{
	
}


static void vortex_get_strings(struct net_device *dev, u32 stringset, u8 *data)
{
	switch (stringset) {
	case ETH_SS_STATS:
		memcpy(data, &ethtool_stats_keys, sizeof(ethtool_stats_keys));
		break;
	default:
		WARN_ON(1);
		break;
	}
}

static void vortex_get_drvinfo(struct net_device *dev,
					struct ethtool_drvinfo *info)
{
	struct vortex_private *vp = netdev_priv(dev);

	strcpy(info->driver, DRV_NAME);
	if (VORTEX_PCI(vp)) {
		strcpy(info->bus_info, pci_name(VORTEX_PCI(vp)));
	} else {
		if (VORTEX_EISA(vp))
			strcpy(info->bus_info, dev_name(vp->gendev));
		else
			sprintf(info->bus_info, "EISA 0x%lx %d",
					dev->base_addr, dev->irq);
	}
}

static void vortex_get_wol(struct net_device *dev, struct ethtool_wolinfo *wol)
{
	struct vortex_private *vp = netdev_priv(dev);

	if (!VORTEX_PCI(vp))
		return;

	wol->supported = WAKE_MAGIC;

	wol->wolopts = 0;
	if (vp->enable_wol)
		wol->wolopts |= WAKE_MAGIC;
}

static int vortex_set_wol(struct net_device *dev, struct ethtool_wolinfo *wol)
{
	struct vortex_private *vp = netdev_priv(dev);

	if (!VORTEX_PCI(vp))
		return -EOPNOTSUPP;

	if (wol->wolopts & ~WAKE_MAGIC)
		return -EINVAL;

	if (wol->wolopts & WAKE_MAGIC)
		vp->enable_wol = 1;
	else
		vp->enable_wol = 0;
	acpi_set_WOL(dev);

	return 0;
}



#ifdef PCI
/*
 *	Must power the device up to do MDIO operations
 */
static int vortex_ioctl(struct net_device *dev, struct ifreq *rq, int cmd)
{
	return 0;
}
#endif


/* Pre-Cyclone chips have no documented multicast filter, so the only
   multicast setting is to receive all multicast frames.  At least
   the chip has a very clean way to set the mode, unlike many others. */
static void set_rx_mode(struct net_device *dev)
{
	
	int new_mode;

	
}

#if defined(CONFIG_VLAN_8021Q) || defined(CONFIG_VLAN_8021Q_MODULE)
/* Setup the card so that it can receive frames with an 802.1q VLAN tag.
   Note that this must be done after each RxReset due to some backwards
   compatibility logic in the Cyclone and Tornado ASICs */

/* The Ethernet Type used for 802.1q tagged frames */
#define VLAN_ETHER_TYPE 0x8100

static void set_8021q_mode(struct net_device *dev, int enable)
{
	struct vortex_private *vp = netdev_priv(dev);
	int mac_ctrl;

	
}
#else

static void set_8021q_mode(struct net_device *dev, int enable)
{
}


#endif

/* MII transceiver control section.
   Read and write the MII registers using software-generated serial
   MDIO protocol.  See the MII specifications or DP83840A data sheet
   for details. */

/* The maximum data clock rate is 2.5 Mhz.  The minimum timing is usually
   met by back-to-back PCI I/O cycles, but we insert a delay to avoid
   "overclocking" issues. */
static void mdio_delay(struct vortex_private *vp)
{
	window_read32(vp, 4, Wn4_PhysicalMgmt);
}


static void mdio_sync(struct vortex_private *vp, int bits)
{
	
}

static int mdio_read(struct net_device *dev, int phy_id, int location)
{
	return 0;
}

static void mdio_write(struct net_device *dev, int phy_id, int location, int value)
{
	spin_lock_bh(&vp->mii_lock);

	
}

/* ACPI: Advanced Configuration and Power Interface. */
/* Set Wake-On-LAN mode and put the board into D3 (power-down) state. */
static void acpi_set_WOL(struct net_device *dev)
{
	
}


static void vortex_remove_one(struct pci_dev *pdev)
{
	free_netdev(dev);
}


static int vortex_have_pci;
static int vortex_have_eisa;


static int  vortex_init(void)
{
	return 0;
}


static void vortex_eisa_cleanup(void)
{

#ifdef CONFIG_EISA
	int x;
#endif
}

module_init(vortex_init);
module_exit(vortex_cleanup);
