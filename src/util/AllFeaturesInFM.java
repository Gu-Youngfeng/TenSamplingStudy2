package util;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AllFeaturesInFM {

	public static void main(String[] args) throws Exception {
		File sourceFile = new File("bugs/busybox/archival/tar.c");
		List<String> directives = new AllFeaturesInFM().getDirectives(sourceFile);
		
		List<String> directivesInFM = new AllFeaturesInFM().getDirectivesInFM();
		
		for (String directive : directives){
			if (!directivesInFM.contains(directive)){
				System.out.println("Problem with feature: " + directive);
			}
		}
		
	}
	
	public List<String> getDirectivesInFM() throws Exception {
		List<String> directives = new ArrayList<String>();
		FileInputStream fis = new FileInputStream(new File("featureModel/busybox.dimacs"));
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		while ((line = br.readLine()) != null) {
			if (line.startsWith("p cnf")){
				break;
			}
			System.out.println(line);
			String[] parts = line.split(" ");
			directives.add(parts[2].trim());
		}
	 
		br.close();
		return directives;
	}
	
	public boolean isValidJavaIdentifier(String s) {
		if (s.equals("64BIT")){
			return true;
		}
		
		// An empty or null string cannot be a valid identifier.
	    if (s == null || s.length() == 0){
	    	return false;
	   	}

	    char[] c = s.toCharArray();
	    if (!Character.isJavaIdentifierStart(c[0])){
	    	return false;
	    }

	    for (int i = 1; i < c.length; i++){
	        if (!Character.isJavaIdentifierPart(c[i])){
	           return false;
	        }
	    }

	    return true;
	}
	
	// It sets the number of configurations..
	public List<String> getDirectives(File file) throws Exception{
		List<String> directives = new ArrayList<>();
		
		FileInputStream fstream = new FileInputStream(file);
		DataInputStream in = new DataInputStream(fstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String strLine;

		while ((strLine = br.readLine()) != null)   {
			
			strLine = strLine.trim();
			
			if (strLine.startsWith("#")){
				strLine = strLine.replaceAll("#(\\s)+", "#");
				strLine = strLine.replaceAll("#(\\t)+", "#");
			}
			
			if (strLine.trim().startsWith("#if") || strLine.trim().startsWith("#elif")){
				
				strLine = strLine.replaceAll("(?:/\\*(?:[^*]|(?:\\*+[^*/]))*\\*+/)|(?://.*)","");
				
				String directive = strLine.replace("#ifdef", "").replace("#ifndef", "").replace("#if", "");
				directive = directive.replace("defined", "").replace("(", "").replace(")", "");
				directive = directive.replace("||", "").replace("&&", "").replace("!", "").replace("<", "").replace(">", "").replace("=", "");
				
				String[] directivesStr = directive.split(" ");
				
				for (int i = 0; i < directivesStr.length; i++){
					if (!directives.contains(directivesStr[i].trim()) && !directivesStr[i].trim().equals("") && this.isValidJavaIdentifier(directivesStr[i].trim())){
						directives.add(directivesStr[i].trim());
					}
				}
			}
			
			if (strLine.startsWith("#")){
				strLine = strLine.replaceAll("#(\\s)+", "#");
				strLine = strLine.replaceAll("#(\\t)+", "#");
				if (strLine.startsWith("#define")){
					String[] parts = strLine.split(" ");
					if (parts.length == 3){
						parts[1] = parts[1].trim();
						directives.remove(parts[1]);
					}
				}
			}
			
		}
		
		String fileName = file.getName().toUpperCase() + "_H";
		directives.remove(fileName);
		
		in.close();
		return directives;
	}
}
