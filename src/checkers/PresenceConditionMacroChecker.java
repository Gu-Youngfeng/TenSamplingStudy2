package checkers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class PresenceConditionMacroChecker {

	public static void main(String[] args) throws IOException {
		FileInputStream fis = new FileInputStream(new File("bugs/busybox/busybox-bugs"));
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(";");
			String presenceCondition = parts[3];
			
			presenceCondition = presenceCondition.replaceAll("\\s", "");
			String[] options = presenceCondition.split("\\)\\|\\|\\(");
			
			int numberOfMacros = 100000;
			
			for (String option : options){
				String[] macros = option.split("&&");
				if (macros.length < numberOfMacros){
					numberOfMacros = macros.length;
				}
			}
			
			System.out.println(parts[0] + "/" + parts[1] + "/" + parts[2] + ": " + numberOfMacros + ".");
			
		}
	 
		br.close();
	}
	
}
