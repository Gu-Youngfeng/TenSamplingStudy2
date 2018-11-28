package test;

import java.io.File;
import java.util.List;

import core.algorithms.ConstrainedOneDisabledOnewise;

public class TestAllEnabledDisabledOnewise {

	public static void main(String[] args) throws Exception {
		//TestAllEnabledDisabledOnewise tester = new TestAllEnabledDisabledOnewise();
		
		File csvFile = new File("featureModel/busybox.dimacs.ca1.csv");
		File srcFile = new File("bugs/busybox/archival/tar.c");
		List<List<String>> samplings = new ConstrainedOneDisabledOnewise().getSamples(srcFile, csvFile);
		
		for (List<String> s : samplings){
			//System.out.println(tester.getNumberOfMacrosEnabled(s));
			System.out.println(s);
		}
		
	}
	
	public int getNumberOfMacrosEnabled(List<String> configuration){
		int count = 0;
		for (String macro : configuration){
			if (!macro.startsWith("!")){
				count++;
			}
		}
		return count;
	}
	
}
