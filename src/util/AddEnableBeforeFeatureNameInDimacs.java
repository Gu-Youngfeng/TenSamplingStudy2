package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class AddEnableBeforeFeatureNameInDimacs {

	public static void main(String[] args) throws IOException {
		FileInputStream fis = new FileInputStream(new File("featureModel/busybox.dimacs"));
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(" ");
			String replacement = parts[2].trim();
			if (!replacement.startsWith("ENABLE")){
				line = line.replace(replacement, "ENABLE_" + replacement);
			}
			System.out.println(line);
		}
	 
		br.close();
	}
	
}
