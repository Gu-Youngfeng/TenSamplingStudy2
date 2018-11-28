package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

import core.SamplingAlgorithm;
import core.algorithms.ConstrainedAllEnabledDisabledSat4j;
import core.algorithms.ConstrainedOneDisabledSat4j;
import core.algorithms.ConstrainedOneEnabledSat4j;

public class TestConstrainedAllEnabledDisabled {

	public static int bugs = 0;
	public static int configurations = 0;
	
	public static int lastConfigNumber = 0;
	
	public static void main(String[] args) throws Exception {
		
		TestConstrainedAllEnabledDisabled t = new TestConstrainedAllEnabledDisabled();
		
		FileInputStream fis = new FileInputStream(new File("bugs/busybox/busybox-bugs"));
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(";");
			String presenceCondition = parts[3];
				
			presenceCondition = presenceCondition.replaceAll("\\s", "");
			String[] options = presenceCondition.split("\\)\\|\\|\\(");
			
			boolean detectedBug = false;
				
			for (String option : options){
				String[] macros = option.split("&&");
				configurations = 0;
				if (t.detectedBug(new ConstrainedAllEnabledDisabledSat4j(), new File("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2]), macros, "featureModel/busybox.dimacs")){
					detectedBug = true;
				}
			}
			
			if (detectedBug){
				System.out.println("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2] + ". Yes! Configurations: " + configurations + ".");
				bugs++;
			} else {
				System.out.println("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2] + ". No! Configurations: " + configurations + ".");
			}
		}
		br.close();
	}

	public boolean detectedBug(SamplingAlgorithm sampling, File srcFile, String[] macros, String dimacsFile) throws Exception{
		List<List<String>> samplings = null;
		
		if (sampling instanceof ConstrainedAllEnabledDisabledSat4j){
			samplings = ((ConstrainedAllEnabledDisabledSat4j) sampling).getSamples(srcFile, new File(dimacsFile));
		} else if (sampling instanceof ConstrainedOneEnabledSat4j){
			samplings = ((ConstrainedOneEnabledSat4j) sampling).getSamples(srcFile, new File(dimacsFile));
		} else if (sampling instanceof ConstrainedOneDisabledSat4j){
			samplings = ((ConstrainedOneDisabledSat4j) sampling).getSamples(srcFile, new File(dimacsFile));
		}
		
		configurations += samplings.size();
		
		lastConfigNumber = samplings.size();
		
		for (List<String> configuration : samplings){
			boolean detectedBug = true;
			for (String macro : macros){
				macro = macro.replace("(", "").replace(")", "");
				
				if (!configuration.contains(macro)){
					detectedBug = false;
				}
			}
			if (detectedBug){
				return true;
			}
		}
		return false;
	}
	
}
