package core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/***
 * <p>CoveringArraysUtils 类提供了读取 CSV 文件 (由工具 SPLCAT 产生的约束求解的答案) 中有效配置的方法 {@link#getValidProducts}。</p>
 * @editor yongfeng
 */
public class CoveringArraysUtils {

	public static void main(String[] args) throws IOException {
		CoveringArraysUtils coveringArraysUtils = new CoveringArraysUtils();
		List<List<String>> configurationsPairwise = coveringArraysUtils.getValidProducts(new File("featureModel/busybox.dimacs.ca2.csv"));
		for (List<String> configuration : configurationsPairwise){
			System.out.println(configuration);
		}
	}
	
	// Receives a CSV file and returns the valid products.
	/***
	 * <p>读取 file 文件 (即工具 SPLCA Tool 生成的文件) 中的配置组合</p>
	 * @param file SPLCA Tool 生成的文件
	 * @return 配置组合的集合
	 * @throws IOException
	 */
	public List<List<String>> getValidProducts(File file) throws IOException{
		List<List<String>> configurations = new ArrayList<List<String>>();
		
		FileInputStream fis = new FileInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		
		// First line of the covering array.
		if ((line = br.readLine()) != null){ // 读 CSV 文件第一行
			String[] parts = line.split(";"); // parts 数组记录了一共生成了多少配置组合
			for (int i = 1; i < parts.length; i++){
				List<String> configuration = new ArrayList<String>();
				configurations.add(configuration); // 将空的配置组合放入到 configurations 集合中去
			}
		}
		
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(";");
			for (int i = 1; i < parts.length; i++){
				List<String> configuration = configurations.get((i-1));
				parts[i] = parts[i].trim();
				if (parts[i].equals("X")){
					configuration.add(parts[0]);
				} else {
					configuration.add("!" + parts[0]);
				}
			}
		}
	 
		br.close();
		
		return configurations;
	}
	
	// Receives a CSV file and returns the valid products.
	/***
	 * <p>读取 file 文件 (即工具 SPLCA Tool 生成的文件) 中的配置组合</p>
	 * @param file SPLCA Tool 生成的文件
	 * @param directives  
	 * @return 配置组合的集合
	 * @throws IOException
	 */
	public List<List<String>> getValidProducts(File file, List<String> directives) throws IOException{
		//System.out.println(directives);
		List<List<String>> configurations = new ArrayList<List<String>>();
		
		FileInputStream fis = new FileInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
	 
		String line = null;
		
		// First line of the covering array.
		if ((line = br.readLine()) != null){
			String[] parts = line.split(";");
			for (int i = 1; i < parts.length; i++){
				List<String> configuration = new ArrayList<String>();
				configurations.add(configuration);
			}
		}
		
		while ((line = br.readLine()) != null) {
			String[] parts = line.split(";");
			for (int i = 1; i < parts.length; i++){
				List<String> configuration = configurations.get((i-1));
				parts[i] = parts[i].trim();
				
				if (directives.contains(parts[0])){
					if (parts[i].equals("X")){
						configuration.add(parts[0]);
					} else {
						configuration.add("!" + parts[0]);
					}
				}
			}
		}
	 
		br.close();
		
		// Sorting lists to remove duplication of configurations..
		for (List<String> configuration : configurations){
			Collections.sort(configuration);
		}
		
		List<List<String>> samplings = new ArrayList<List<String>>();
		for (List<String> configuration : configurations){
			if (!samplings.contains(configuration)){
				samplings.add(configuration);
			}
		}
		return samplings;
	}
	
}
