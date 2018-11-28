package checkers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

import core.SamplingAlgorithm;
import core.algorithms.ConstrainedAllEnabledDisabledOnewise;
import core.algorithms.ConstrainedOneDisabledOnewise;
import core.algorithms.ConstrainedOneEnabledOnewise;

public class BugCheckerAdditionalSamplings {

	public static int bugs = 0;
	public static int configurations = 0;
	
	public static void main(String[] args) throws Exception {
		BugCheckerAdditionalSamplings bugCheckerAdditionalSamplings = new BugCheckerAdditionalSamplings();
		
		ConstrainedAllEnabledDisabledOnewise allEnabledDisabled = new ConstrainedAllEnabledDisabledOnewise();
		ConstrainedOneEnabledOnewise oneEnabled = new ConstrainedOneEnabledOnewise();
		ConstrainedOneDisabledOnewise oneDisabled = new ConstrainedOneDisabledOnewise();
		
		double start = System.currentTimeMillis();
		System.out.println("All-Enabled-Disabled");
		bugCheckerAdditionalSamplings.checkSampling(allEnabledDisabled, "featureModel/busybox.dimacs.ca1.csv", "featureModel/linux.dimacs.ca1.csv");
		double end = System.currentTimeMillis();
		System.out.println("TIME: " + (end-start));
		System.out.println("AVERAGE: " + ((end-start)/47));
		
		start = System.currentTimeMillis();
		System.out.println("One-Enabled");
		bugCheckerAdditionalSamplings.checkSampling(oneEnabled, "featureModel/busybox.dimacs.ca1.csv", "featureModel/linux.dimacs.ca1.csv");
		end = System.currentTimeMillis();
		System.out.println("TIME: " + (end-start));
		System.out.println("AVERAGE: " + ((end-start)/47));
		
		start = System.currentTimeMillis();
		System.out.println("One-Disabled");
		bugCheckerAdditionalSamplings.checkSampling(oneDisabled, "featureModel/busybox.dimacs.ca1.csv", "featureModel/linux.dimacs.ca1.csv");
		end = System.currentTimeMillis();
		System.out.println("TIME: " + (end-start));
		System.out.println("AVERAGE: " + ((end-start)/47));
		
	}
	
	public void checkSampling(SamplingAlgorithm sampling, String dimacsFile1, String dimacsFile2) throws Exception {
		bugs = 0;
		configurations = 0;
				
		FileInputStream fis = new FileInputStream(new File("bugs/busybox/busybox-bugs"));
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(";");
			String presenceCondition = parts[3];
			
			presenceCondition = presenceCondition.replaceAll("\\s", "");
			String[] options = presenceCondition.split("\\)\\|\\|\\(");
			
			boolean detectedBug = false;
			List<List<String>> samplings = null;
			File srcFile = new File("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2]);
			
			
			if (sampling instanceof ConstrainedAllEnabledDisabledOnewise){
				samplings = ((ConstrainedAllEnabledDisabledOnewise) sampling).getSamples(srcFile, new File(dimacsFile1));
			} else if (sampling instanceof ConstrainedOneEnabledOnewise){
				samplings = ((ConstrainedOneEnabledOnewise) sampling).getSamples(srcFile, new File(dimacsFile1));
			} else if (sampling instanceof ConstrainedOneDisabledOnewise){
				samplings = ((ConstrainedOneDisabledOnewise) sampling).getSamples(srcFile, new File(dimacsFile1));
			}
			
			configurations += samplings.size();
			
			for (String option : options){
				String[] macros = option.split("&&");
				if (this.detectedBug(samplings, macros)){
					detectedBug = true;
				}
			}
			
			if (detectedBug){
				bugs++;
			}
			
		}
	 
		br.close();
		
		fis = new FileInputStream(new File("bugs/linux/linux-bugs"));
		br = new BufferedReader(new InputStreamReader(fis));
	 
		line = null;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(";");
			String presenceCondition = parts[3];
			
			presenceCondition = presenceCondition.replaceAll("\\s", "");
			String[] options = presenceCondition.split("\\)\\|\\|\\(");
			
			boolean detectedBug = false;
			List<List<String>> samplings = null;
			File srcFile = new File("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2]);
			
			
			if (sampling instanceof ConstrainedAllEnabledDisabledOnewise){
				samplings = ((ConstrainedAllEnabledDisabledOnewise) sampling).getSamples(srcFile, new File(dimacsFile1));
			} else if (sampling instanceof ConstrainedOneEnabledOnewise){
				samplings = ((ConstrainedOneEnabledOnewise) sampling).getSamples(srcFile, new File(dimacsFile1));
			} else if (sampling instanceof ConstrainedOneDisabledOnewise){
				samplings = ((ConstrainedOneDisabledOnewise) sampling).getSamples(srcFile, new File(dimacsFile1));
			}
			for (String option : options){
				String[] macros = option.split("&&");
				if (this.detectedBug(samplings, macros)){
					detectedBug = true;
				}
			}
			
			if (detectedBug){
				bugs++;
			} 
			
		}
	 
		br.close();
		this.listAllFiles(new File("code"), sampling);
		
		System.out.println("Bugs: " + bugs);
		System.out.println("Configurations: " + configurations + "\n");
	}
	
	public void listAllFiles(File path, SamplingAlgorithm algorithm) throws Exception{
		if (path.isDirectory()){
			for (File file : path.listFiles()){
				this.listAllFiles(file, algorithm);
			}
		} else {
			if (path.getName().endsWith(".c")){
				if (algorithm instanceof ConstrainedAllEnabledDisabledOnewise){
					configurations += 2;
				} else {
					int d = algorithm.getDirectives(path).size();
					if (d == 1){
						configurations += 1;
					} else {
						configurations += d;
					}
				} 
			}
		}
	}
	
	public boolean detectedBug(List<List<String>> samplings, String[] macros) throws Exception{
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
