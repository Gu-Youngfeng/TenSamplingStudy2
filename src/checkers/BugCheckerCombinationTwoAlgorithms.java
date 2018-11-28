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
import core.algorithms.ConstrainedStmtCoverageSampling;
import core.algorithms.ConstrainedTwiseSampling;

public class BugCheckerCombinationTwoAlgorithms {

	static int configurations = 0;
	static int bugs = 0;
	
	public static int lastConfigNumber = 0;
	
	public static void main(String[] args) throws Exception {
		BugCheckerCombinationTwoAlgorithms checker = new BugCheckerCombinationTwoAlgorithms();
		
		configurations = 0;
		bugs = 0;
		System.out.println("Pairwise and One-Enabled: ");
		checker.checkSampling(new ConstrainedTwiseSampling(2), new ConstrainedOneEnabledOnewise());
		
		configurations = 0;
		bugs = 0;
		System.out.println("Pairwise and One-Disabled:");
		checker.checkSampling(new ConstrainedTwiseSampling(2), new ConstrainedOneDisabledOnewise());
		
		configurations = 0;
		bugs = 0;
		System.out.println("Pairwise and All-Enabled-Disabled:");
		checker.checkSampling(new ConstrainedTwiseSampling(2), new ConstrainedAllEnabledDisabledOnewise());
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("Threewise and One-Enabled:");
//		checker.checkSampling(new ConstrainedTwiseSampling(3), new ConstrainedOneEnabled());
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("Fourwise and One-Enabled:");
//		checker.checkSampling(new ConstrainedTwiseSampling(4), new ConstrainedOneEnabled());
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("Threewise and One-Disabled:");
//		checker.checkSampling(new ConstrainedTwiseSampling(3), new ConstrainedOneDisabled());
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("Fourwise and One-Disabled:");
//		checker.checkSampling(new ConstrainedTwiseSampling(4), new ConstrainedOneDisabled());
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("Threewise and All-Enabled-Disabled:");
//		checker.checkSampling(new ConstrainedTwiseSampling(3), new ConstrainedAllEnabledDisabled());
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("Fourwise and All-Enabled-Disabled:");
//		checker.checkSampling(new ConstrainedTwiseSampling(4), new ConstrainedAllEnabledDisabled());
		
		configurations = 0;
		bugs = 0;
		System.out.println("One-Enabled and All-Enabled-Disabled:");
		checker.checkSampling(new ConstrainedOneEnabledOnewise(), new ConstrainedAllEnabledDisabledOnewise());
		
		configurations = 0;
		bugs = 0;
		System.out.println("One-Enabled and One-Disabled:");
		checker.checkSampling(new ConstrainedOneEnabledOnewise(), new ConstrainedOneDisabledOnewise());
		
		configurations = 0;
		bugs = 0;
		System.out.println("One-Disabled and All-Enabled-Disabled:");
		checker.checkSampling(new ConstrainedOneDisabledOnewise(), new ConstrainedAllEnabledDisabledOnewise());
		
		configurations = 0;
		bugs = 0;
		System.out.println("Stmt-coverage and One-Enabled:");
		checker.checkSampling(new ConstrainedStmtCoverageSampling(), new ConstrainedOneDisabledOnewise());
		
		configurations = 0;
		bugs = 0;
		System.out.println("Stmt-coverage and One-disabled:");
		checker.checkSampling(new ConstrainedStmtCoverageSampling(), new ConstrainedOneDisabledOnewise());
		
		configurations = 0;
		bugs = 0;
		System.out.println("Stmt coverage and All-enabled-disabled:");
		checker.checkSampling(new ConstrainedStmtCoverageSampling(), new ConstrainedAllEnabledDisabledOnewise());
		
		configurations = 0;
		bugs = 0;
		System.out.println("Stmt-coverage and Pairwise:");
		checker.checkSampling(new ConstrainedStmtCoverageSampling(), new ConstrainedTwiseSampling(2));
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("Stmt-coverage and Threewise:");
//		checker.checkSampling(new StmtCoverageSampling(), new ConstrainedTwiseSampling(3));
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("Stmt-coverage and Fourwise:");
//		checker.checkSampling(new StmtCoverageSampling(), new ConstrainedTwiseSampling(4));
	}
	
	public void checkSampling(SamplingAlgorithm sampling1, SamplingAlgorithm sampling2) throws Exception {
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
			
			for (String option : options){
				String[] macros = option.split("&&");
				if (this.doesSamplingWork(new File("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2]), macros, sampling1, sampling2, "busybox")){
					detectedBug = true;
				}
			}
			
			if (detectedBug){
				System.out.println(parts[0] + "/" + parts[1] + "/" + parts[2] + ": yes! Configurations: " + lastConfigNumber + ".");
				bugs++;
			} else {
				System.out.print(parts[0] + "/" + parts[1] + "/" + parts[2] + ": no. Configurations: " + lastConfigNumber + ".");
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
			
			for (String option : options){
				String[] macros = option.split("&&");
				if (this.doesSamplingWork(new File("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2]), macros, sampling1, sampling2, "linux")){
					detectedBug = true;
				}
			}
			
			if (detectedBug){
				System.out.println(parts[0] + "/" + parts[1] + "/" + parts[2] + ": yes! Configurations: " + lastConfigNumber + ".");
				bugs++;
			} else {
				System.out.print(parts[0] + "/" + parts[1] + "/" + parts[2] + ": no. Configurations: " + lastConfigNumber + ".");
			}
			
		}
	 
		br.close();
		
		
		System.out.println("Bugs: " + bugs);
		System.out.println("Configurations: " + configurations + "\n");
	}
	
	public boolean doesSamplingWork(File file, String[] macros, SamplingAlgorithm samplingAlgorithm1, SamplingAlgorithm samplingAlgorithm2, String project) throws Exception{
		
		List<List<String>> sampling1 = null;
		List<List<String>> sampling2 = null;
		
		
		if (samplingAlgorithm1 instanceof ConstrainedTwiseSampling){
			if (project.equals("busybox")){
				sampling1 = ((ConstrainedTwiseSampling)samplingAlgorithm1).getSamples(file, new File("featureModel/busybox.dimacs.ca2.csv"));
			} else if (project.equals("linux")){
				sampling1 = ((ConstrainedTwiseSampling)samplingAlgorithm1).getSamples(file,  new File("featureModel/linux.dimacs.ca2.csv"));
			}
		} else if (samplingAlgorithm1 instanceof ConstrainedAllEnabledDisabledOnewise){
			if (project.equals("busybox")){
				sampling1 = ((ConstrainedAllEnabledDisabledOnewise)samplingAlgorithm1).getSamples(file, new File("featureModel/busybox.dimacs.ca1.csv"));
			} else if (project.equals("linux")){
				sampling1 = ((ConstrainedAllEnabledDisabledOnewise)samplingAlgorithm1).getSamples(file,  new File("featureModel/linux.dimacs.ca1.csv"));
			}
		} else if (samplingAlgorithm1 instanceof ConstrainedOneDisabledOnewise){
			if (project.equals("busybox")){
				sampling1 = ((ConstrainedOneDisabledOnewise)samplingAlgorithm1).getSamples(file, new File("featureModel/busybox.dimacs.ca1.csv"));
			} else if (project.equals("linux")){
				sampling1 = ((ConstrainedOneDisabledOnewise)samplingAlgorithm1).getSamples(file,  new File("featureModel/linux.dimacs.ca1.csv"));
			}
		} else if (samplingAlgorithm1 instanceof ConstrainedOneEnabledOnewise){
			if (project.equals("busybox")){
				sampling1 = ((ConstrainedOneEnabledOnewise)samplingAlgorithm1).getSamples(file, new File("featureModel/busybox.dimacs.ca1.csv"));
			} else if (project.equals("linux")){
				sampling1 = ((ConstrainedOneEnabledOnewise)samplingAlgorithm1).getSamples(file,  new File("featureModel/linux.dimacs.ca1.csv"));
			}
		} else {
			sampling1 = samplingAlgorithm1.getSamples(file);
		}
		
		if (samplingAlgorithm2 instanceof ConstrainedTwiseSampling){
			if (project.equals("busybox")){
				sampling2 = ((ConstrainedTwiseSampling)samplingAlgorithm2).getSamples(file, new File("featureModel/busybox.dimacs.ca2.csv"));
			} else if (project.equals("linux")){
				sampling2 = ((ConstrainedTwiseSampling)samplingAlgorithm2).getSamples(file,  new File("featureModel/linux.dimacs.ca2.csv"));
			}
		} else if (samplingAlgorithm2 instanceof ConstrainedAllEnabledDisabledOnewise){
			if (project.equals("busybox")){
				sampling2 = ((ConstrainedAllEnabledDisabledOnewise)samplingAlgorithm2).getSamples(file, new File("featureModel/busybox.dimacs.ca1.csv"));
			} else if (project.equals("linux")){
				sampling2 = ((ConstrainedAllEnabledDisabledOnewise)samplingAlgorithm2).getSamples(file,  new File("featureModel/linux.dimacs.ca1.csv"));
			}
		} else if (samplingAlgorithm2 instanceof ConstrainedOneDisabledOnewise){
			if (project.equals("busybox")){
				sampling2 = ((ConstrainedOneDisabledOnewise)samplingAlgorithm2).getSamples(file, new File("featureModel/busybox.dimacs.ca1.csv"));
			} else if (project.equals("linux")){
				sampling2 = ((ConstrainedOneDisabledOnewise)samplingAlgorithm2).getSamples(file,  new File("featureModel/linux.dimacs.ca1.csv"));
			}
		} else if (samplingAlgorithm2 instanceof ConstrainedOneEnabledOnewise){
			if (project.equals("busybox")){
				sampling2 = ((ConstrainedOneEnabledOnewise)samplingAlgorithm2).getSamples(file, new File("featureModel/busybox.dimacs.ca1.csv"));
			} else if (project.equals("linux")){
				sampling2 = ((ConstrainedOneEnabledOnewise)samplingAlgorithm2).getSamples(file,  new File("featureModel/linux.dimacs.ca1.csv"));
			}
		} else {
			sampling2 = samplingAlgorithm2.getSamples(file);
		}
		
		configurations += (sampling1.size() + sampling2.size());
		lastConfigNumber = (sampling1.size() + sampling2.size());
		
		for (List<String> s : sampling2){
			sampling1.add(s);
		}
		
		for (List<String> configuration : sampling1){
			boolean containsAll = true;
			for (String macro : macros){
				macro = macro.replace("(", "").replace(")", "").replaceAll("\\s", "");
				if (!configuration.contains(macro)){
					containsAll = false;
				}
			}
			if (containsAll){
				return true;
			}
		}
		return false;
	}
	
}
