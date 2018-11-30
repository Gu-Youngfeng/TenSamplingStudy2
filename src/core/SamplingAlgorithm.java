package core;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/***
 * <p><b>SamplingAlgorithm</b> 类是一个抽象类，所有的 core.algorithm 内的算法都继承自该类。</p>
 * @editor yongfeng
 * @see#getSamples
 * @see#getDirectives
 * @see#getNumberOfMacrosEnabled
 */
public abstract class SamplingAlgorithm {

	protected List<String> directives;
	
	/**
	 * <p>利用采样算法获取采样的配置组合，该方法为抽样方法需要被子类实现。</p>
	 * @param file C 文件
	 * @return 满足条件的配置组合的集合
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
	 * <p>获取 C 文件中配置项的个数</p>
	 * @param file C 文件路径
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
	 * <p>判断一个配置组合中有多少个配置项是启用的</p>
	 * @param configuration 配置组合
	 * @return 启用的配置项的个数
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
