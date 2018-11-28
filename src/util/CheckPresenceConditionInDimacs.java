package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CheckPresenceConditionInDimacs {

	public static void main(String[] args) throws Exception {
		String dimacsFile = new CheckPresenceConditionInDimacs().readFile("featureModel/busybox.dimacs");
		
		FileInputStream fis = new FileInputStream(new File("bugs/busybox/busybox-bugs"));
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		while ((line = br.readLine()) != null) {
			String presenceCondition = line.split(";")[3];
			
			presenceCondition = presenceCondition.replaceAll("\\s", "");
			String[] options = presenceCondition.split("\\)\\|\\|\\(");
			
			for (String option : options){
				String[] macros = option.split("&&");
				for (String macro : macros){
					macro = macro.replace("(", "").replace(")", "").replace("!", "");
					//TODO: NOT WORKING WELL - ENABLE_FEATURE_SYSLOG MISSING.. 
					if (!dimacsFile.contains(macro)){
						System.out.println("Missing macro: " + macro);
					}
				}
			}
			
		}
	 
		br.close();
	}
	
	public String readFile( String file ) throws IOException {
	    BufferedReader reader = new BufferedReader( new FileReader (file));
	    String         line = null;
	    StringBuilder  stringBuilder = new StringBuilder();
	    String         ls = System.getProperty("line.separator");

	    while( ( line = reader.readLine() ) != null ) {
	        stringBuilder.append( line );
	        stringBuilder.append( ls );
	    }
	    
	    reader.close();
	    return stringBuilder.toString();
	}
}
