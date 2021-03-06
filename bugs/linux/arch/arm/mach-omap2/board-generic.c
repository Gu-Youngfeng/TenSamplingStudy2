/*
 * linux/arch/arm/mach-omap2/board-generic.c
 *
 * Copyright (C) 2005 Nokia Corporation
 * Author: Paul Mundt <paul.mundt@nokia.com>
 *
 * Modified from mach-omap/omap1/board-generic.c
 *
 * Code for generic OMAP2 board. Should work on many OMAP2 systems where
 * the bootloader passes the board-specific data to the kernel.
 * Do not put any board specific code to this file; create a new machine
 * type if you need custom low-level initializations.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 */

#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/device.h>

#include <mach/hardware.h>
#include <asm/mach-types.h>
#include <asm/mach/arch.h>
#include <asm/mach/map.h>

#include <mach/gpio.h>
#include <plat/usb.h>
#include <plat/board.h>

#include <plat/common.h>


static struct omap_board_config_kernel generic_config[] = {
};

static void omap_generic_init_irq(void)
{
#ifdef MACH_OMAP_H4_OTG
#ifdef ARCH_OMAP2430
	omap_board_config = generic_config;
	omap_board_config_size = ARRAY_SIZE(generic_config);
	omap2_init_common_hw(NULL, NULL);
	omap_init_irq();
#endif
#endif
}

static void omap_generic_init(void)
{
	omap_serial_init();
}

static void omap_generic_map_io(void)
{
	if (cpu_is_omap242x()) {
		omap2_set_globals_242x();
		omap242x_map_common_io();
	} else if (cpu_is_omap243x()) {
		omap2_set_globals_243x();
		omap243x_map_common_io();
	} else if (cpu_is_omap34xx()) {
		omap2_set_globals_3xxx();
		omap34xx_map_common_io();
	} else if (cpu_is_omap44xx()) {
		omap2_set_globals_443x();
		omap44xx_map_common_io();
	}
}


