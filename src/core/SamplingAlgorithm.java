package core;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/***
 * <p><b>SamplingAlgorithm</b> ����һ�������࣬���е� core.algorithm �ڵ��㷨���̳��Ը��ࡣ</p>
 * @editor yongfeng
 * @see#getSamples
 * @see#getDirectives
 * @see#getNumberOfMacrosEnabled
 */
public abstract class SamplingAlgorithm {

	protected List<String> directives;
	
	/**
	 * <p>���ò����㷨��ȡ������������ϣ��÷���Ϊ����������Ҫ������ʵ�֡�</p>
	 * @param file C �ļ�
	 * @return ����������������ϵļ���
	 * @throws Exception
	 */
	public abstract List<List<String>> getSamples(File file) throws Exception;
	
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
	/**
	 * <p>��ȡ C �ļ���������ĸ���</p>
	 * @param file C �ļ�·��
	 * 
	 */
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
	
	/**
	 * <p>�ж�һ������������ж��ٸ������������õ�</p>
	 * @param configuration �������
	 * @return ���õ�������ĸ���
	 */
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
