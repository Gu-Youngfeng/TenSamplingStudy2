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

public class BugCheckerCombinationThreeAlgorithms {

	static int configurations = 0;
	static int bugs = 0;
	
	public static int lastConfigNumber = 0;
	
	public static void main(String[] args) throws Exception {
		BugCheckerCombinationThreeAlgorithms checker = new BugCheckerCombinationThreeAlgorithms();

		configurations = 0;
		bugs = 0;
		System.out.println("One-Enabled, One-Disabled and All-Enabled-Disabled");
		checker.checkSampling(new ConstrainedOneEnabledOnewise(), new ConstrainedOneDisabledOnewise(), new ConstrainedAllEnabledDisabledOnewise());
		
		configurations = 0;
		bugs = 0;
		System.out.println("One-Enabled, One-Disabled and pair-wise");
		checker.checkSampling(new ConstrainedOneEnabledOnewise(), new ConstrainedOneDisabledOnewise(), new ConstrainedTwiseSampling(2));
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("One-Enabled, One-Disabled and three-wise");
//		checker.checkSampling(new ConstrainedOneEnabled(), new ConstrainedOneDisabled(), new ConstrainedTwiseSampling(3));
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("One-Enabled, One-Disabled and four-wise");
//		checker.checkSampling(new ConstrainedOneEnabled(), new ConstrainedOneDisabled(), new ConstrainedTwiseSampling(4));
		
		configurations = 0;
		bugs = 0;
		System.out.println("One-Enabled, One-Disabled and stmt-coverage");
		checker.checkSampling(new ConstrainedOneEnabledOnewise(), new ConstrainedOneDisabledOnewise(), new ConstrainedStmtCoverageSampling());
		
		configurations = 0;
		bugs = 0;
		System.out.println("One-Enabled, All-Enabled-Disabled and pair-wise");
		checker.checkSampling(new ConstrainedOneEnabledOnewise(), new ConstrainedAllEnabledDisabledOnewise(), new ConstrainedTwiseSampling(2));
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("One-Enabled, All-Enabled-Disabled and three-wise");
//		checker.checkSampling(new ConstrainedOneEnabled(), new ConstrainedAllEnabledDisabled(), new ConstrainedTwiseSampling(3));
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("One-Enabled, All-Enabled-Disabled and four-wise");
//		checker.checkSampling(new ConstrainedOneEnabled(), new ConstrainedAllEnabledDisabled(), new ConstrainedTwiseSampling(4));
		
		configurations = 0;
		bugs = 0;
		System.out.println("One-Enabled, All-Enabled-Disabled and stmt-coverage");
		checker.checkSampling(new ConstrainedOneEnabledOnewise(), new ConstrainedAllEnabledDisabledOnewise(), new ConstrainedStmtCoverageSampling());
	
		configurations = 0;
		bugs = 0;
		System.out.println("One-Enabled, pair-wise and stmt-coverage");
		checker.checkSampling(new ConstrainedOneEnabledOnewise(), new ConstrainedTwiseSampling(2), new ConstrainedStmtCoverageSampling());
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("One-Enabled, three-wise and stmt-coverage");
//		checker.checkSampling(new ConstrainedOneEnabled(), new ConstrainedTwiseSampling(3), new StmtCoverageSampling());
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("One-Enabled, four-wise and stmt-coverage");
//		checker.checkSampling(new ConstrainedOneEnabled(), new ConstrainedTwiseSampling(4), new StmtCoverageSampling());
		
		configurations = 0;
		bugs = 0;
		System.out.println("One-Disabled, All-Enabled-Disabled and pair-wise");
		checker.checkSampling(new ConstrainedOneDisabledOnewise(), new ConstrainedAllEnabledDisabledOnewise(), new ConstrainedTwiseSampling(2));
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("One-Disabled, All-Enabled-Disabled and three-wise");
//		checker.checkSampling(new ConstrainedOneDisabled(), new ConstrainedAllEnabledDisabled(), new ConstrainedTwiseSampling(3));
		
//		configurations = 0;
//		bugs = 0;
//		System.out.println("One-Disabled, All-Enabled-Disabled and four-wise");
//		checker.checkSampling(new ConstrainedOneDisabled(), new ConstrainedAllEnabledDisabled(), new ConstrainedTwiseSampling(4));
		
		configurations = 0;
		bugs = 0;
		System.out.println("One-Disabled, All-Enabled-Disabled and stmt-coverage");
		checker.checkSampling(new ConstrainedOneDisabledOnewise(), new ConstrainedAllEnabledDisabledOnewise(), new ConstrainedStmtCoverageSampling());
		
		configurations = 0;
		bugs = 0;
		System.out.println("All-Enabled-Disabled, pair-wise and stmt-coverage");
		checker.checkSampling(new ConstrainedAllEnabledDisabledOnewise(), new ConstrainedTwiseSampling(2), new ConstrainedStmtCoverageSampling());
	}
	
	public void checkSampling(SamplingAlgorithm sampling1, SamplingAlgorithm sampling2, SamplingAlgorithm sampling3) throws Exception {
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
				if (this.doesSamplingWork(new File("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2]), macros, sampling1, sampling2, sampling3, "busybox")){
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
				if (this.doesSamplingWork(new File("bugs/" + parts[0] + "/" + parts[1] + "/" + parts[2]), macros, sampling1, sampling2, sampling3, "linux")){
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
	
	public boolean doesSamplingWork(File file, String[] macros, SamplingAlgorithm samplingAlgorithm1, SamplingAlgorithm samplingAlgorithm2, SamplingAlgorithm samplingAlgorithm3, String project) throws Exception{
		
		List<List<String>> sampling1 = null;
		List<List<String>> sampling2 = null;
		List<List<String>> sampling3 = null;
		
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
		
		if (samplingAlgorithm3 instanceof ConstrainedTwiseSampling){
			if (project.equals("busybox")){
				sampling3 = ((ConstrainedTwiseSampling)samplingAlgorithm3).getSamples(file, new File("featureModel/busybox.dimacs.ca2.csv"));
			} else if (project.equals("linux")){
				sampling3 = ((ConstrainedTwiseSampling)samplingAlgorithm3).getSamples(file,  new File("featureModel/linux.dimacs.ca2.csv"));
			}
		} else if (samplingAlgorithm3 instanceof ConstrainedAllEnabledDisabledOnewise){
			if (project.equals("busybox")){
				sampling3 = ((ConstrainedAllEnabledDisabledOnewise)samplingAlgorithm3).getSamples(file, new File("featureModel/busybox.dimacs.ca2.csv"));
			} else if (project.equals("linux")){
				sampling3 = ((ConstrainedAllEnabledDisabledOnewise)samplingAlgorithm3).getSamples(file,  new File("featureModel/linux.dimacs.ca2.csv"));
			}
		} else if (samplingAlgorithm3 instanceof ConstrainedOneDisabledOnewise){
			if (project.equals("busybox")){
				sampling3 = ((ConstrainedOneDisabledOnewise)samplingAlgorithm3).getSamples(file, new File("featureModel/busybox.dimacs.ca2.csv"));
			} else if (project.equals("linux")){
				sampling3 = ((ConstrainedOneDisabledOnewise)samplingAlgorithm3).getSamples(file,  new File("featureModel/linux.dimacs.ca2.csv"));
			}
		} else if (samplingAlgorithm3 instanceof ConstrainedOneEnabledOnewise){
			if (project.equals("busybox")){
				sampling3 = ((ConstrainedOneEnabledOnewise)samplingAlgorithm3).getSamples(file, new File("featureModel/busybox.dimacs.ca2.csv"));
			} else if (project.equals("linux")){
				sampling3 = ((ConstrainedOneEnabledOnewise)samplingAlgorithm3).getSamples(file,  new File("featureModel/linux.dimacs.ca2.csv"));
			}
		} else {
			sampling3 = samplingAlgorithm3.getSamples(file);
		}
		
		configurations += (sampling1.size() + sampling2.size() + sampling3.size());
		lastConfigNumber = (sampling1.size() + sampling2.size() + sampling3.size());
		
		for (List<String> s : sampling2){
			sampling1.add(s);
		}
		
		for (List<String> s : sampling3){
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
