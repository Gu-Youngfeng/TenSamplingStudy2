package checkers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

import core.algorithms.ConstrainedTwiseSampling;

public class BugCheckerConstrainedPairWise {

	public static int bugs = 0;
	public static int configurations = 0;
	
	public static void main(String[] args) throws Exception {
		BugCheckerConstrainedPairWise bugChecker = new BugCheckerConstrainedPairWise();
		bugs = 0;
		configurations = 0;
		System.out.println("Constrained Pairwise");
		double start = System.currentTimeMillis();
		bugChecker.checkAlgorithm();
		double end = System.currentTimeMillis();
		System.out.println("TIME: " + (end-start));
		System.out.println("AVERAGE: " + ((end-start)/47));
	}
	
	
	public void checkAlgorithm() throws Exception {
		FileInputStream fis = new FileInputStream(new File("bugs/busybox/busybox-bugs"));
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(";");
			String presenceCondition = parts[3];
			
			presenceCondition = presenceCondition.replaceAll("\\s", "");
			String[] options = presenceCondition.split("\\)\\|\\|\\(");
			
			boolean detectedBug = false;
			
			List<List<String>> samplings = new ConstrainedTwiseSampling().getSamples(new File("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2]), new File("featureModel/busybox.dimacs.ca2.csv"));
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
			
			List<List<String>> samplings = new ConstrainedTwiseSampling().getSamples(new File("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2]), new File("featureModel/linux.dimacs.ca2.csv"));
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
		this.listAllFiles(new File("code"));
		
		System.out.println("Bugs: " + bugs);
		System.out.println("Configurations: " + configurations);
	}
	
	public void listAllFiles(File path) throws Exception{
		if (path.isDirectory()){
			for (File file : path.listFiles()){
				this.listAllFiles(file);
			}
		} else {
			if (path.getName().endsWith(".c")){
				configurations += 30;
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
