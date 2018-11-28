package util;

import java.io.File;
import java.util.List;

import core.algorithms.ConstrainedAllEnabledDisabledSat4j;

public class TestSat4JAllEnabledDisabled {

	public static void main(String[] args) throws Exception {
		ConstrainedAllEnabledDisabledSat4j sampling = new ConstrainedAllEnabledDisabledSat4j();
		List<List<String>> sampleSet = sampling.getSamples(new File("bugs/linux/arch/arm/mach-omap2/board-generic.c"), new File("featureModel/linux.dimacs"));
		System.out.println(sampleSet.size());
	}
	
}
