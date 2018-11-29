package core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.sat4j.core.VecInt;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.reader.DimacsReader;
import org.sat4j.reader.Reader;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IProblem;
import org.sat4j.specs.ISolver;

/***
 * <p>Sat4j 类引入了 sat4j 库，提供了带约束的 One-enabled ({@link#getOneEnabled})，One-disabled ({@link#getOneDisabled}) 的方法实现。</p>
 * <p>sat4j 求解器需要描述特征模型的 CNF 文件，以该约束文件作为输入，以满足条件的配置组合作为输出。</p>
 * @editor yongfeng
 */
public class Sat4j {

	/***
	 * <p>针对 OneEnabled 算法，结合 SAT 求解器接触满足 CNF 约束的，为真最少的配置组合。注意每一个配置项都会被为真一次。</p>
	 * @param file CNF 文件
	 * @return 采样集合
	 * @throws Exception
	 */
	public List<List<String>> getOneEnabled(String file) throws Exception {
		List<List<String>> configurations = new ArrayList<List<String>>();
		for (int i = 1; i < getNumberOfFeaturesInDimac(file); i++){ // CNF 包含 size 个配置项
			int[] clause = {i};
			List<String> configuration = this.getMostDisabled(file, clause); // 获得约束条件下，disabled 最多的配置组合
			configurations.add(configuration);
		}	
		
		return configurations;
	}
	
	/***
	 * <p>针对 Disabled 算法，结合 SAT 求解器接触满足 CNF 约束的，为假最少的配置组合。注意每一个配置项都会被为假一次。</p>
	 * @param file CNF 文件
	 * @return 采样集合
	 * @throws Exception
	 */
	public List<List<String>> getOneDisabled(String file) throws Exception {
		List<List<String>> configurations = new ArrayList<List<String>>();
		for (int i = 1; i < getNumberOfFeaturesInDimac(file); i++){
			int[] clause = {(-1)*i};
			List<String> configuration = this.getMostEnabled(file, clause);
			configurations.add(configuration);
		}	
		
		return configurations;
	}
	
	public List<String> getMostEnabled(String file) throws Exception {
		List<String> configuration = new ArrayList<String>();
		ISolver solver = SolverFactory.newDefault();
		Reader reader = new DimacsReader ( solver );
		
		IProblem problem = reader.parseInstance(file);
		
		for (int j = (problem.nVars()-1); j > 0; j--){
			solver = SolverFactory.newDefault();
			reader = new DimacsReader ( solver );
			
			problem = reader.parseInstance(file);
			
			VecInt v = new VecInt();
			for (int i = 1; i < problem.nVars(); i++){
				v.push(i);
			}
			
			try {
				solver.addAtLeast(v, j);
				if ( problem.isSatisfiable(false)) {
					int[] model = problem.findModel();
					
					for (int i = 1; i <model.length; i++){
						String featureName = this.getVarNameInDimacs(file, i);
						if (model[i] < 0){
							configuration.add("!" + featureName);
						} else {
							configuration.add(featureName);
						}
					}
					
					break;
				}
			} catch (ContradictionException e){
				System.out.println("Not possible to find configuration with at least " + j + " features enabled..");
			}
		}
		
		return configuration;
	}
	
	public List<String> getMostEnabled(String file, int[] clause) throws Exception {
		List<String> configuration = new ArrayList<String>();
		ISolver solver = SolverFactory.newDefault();
		Reader reader = new DimacsReader ( solver );
		
		IProblem problem = reader.parseInstance(file);
		
		for (int j = (problem.nVars()-1); j > 0; j--){
			solver = SolverFactory.newDefault();
			reader = new DimacsReader ( solver );
			
			problem = reader.parseInstance(file);
			
			VecInt v = new VecInt();
			for (int i = 1; i < problem.nVars(); i++){
				v.push(i);
			}
			
			try {
				solver.addAtLeast(v, (j-1));
				solver.addClause(new VecInt(clause));	
				if ( problem.isSatisfiable(false)) {
					int[] model = problem.findModel();
					
					for (int i = 1; i <model.length; i++){
						String featureName = this.getVarNameInDimacs(file, i);
						if (model[i] < 0){
							configuration.add("!" + featureName);
						} else {
							configuration.add(featureName);
						}
					}
					
					break;
				}
			} catch (ContradictionException e){
				System.out.println("Not possible to find configuration with at least " + j + " features enabled..");
			}
		}
		
		return configuration;
	}
	
	public List<String> getMostDisabled(String file) throws Exception {
		List<String> configuration = new ArrayList<String>();
		ISolver solver = SolverFactory.newDefault();
		Reader reader = new DimacsReader ( solver );
		
		IProblem problem = reader.parseInstance(file);
		
		for (int j = 1; j < problem.nVars(); j++){
			solver = SolverFactory.newDefault();
			reader = new DimacsReader ( solver );
			
			problem = reader.parseInstance(file);
			
			VecInt v = new VecInt();
			for (int i = 1; i < problem.nVars(); i++){
				v.push(i);
			}
			try {
				solver.addAtMost(v, j);
				if ( problem.isSatisfiable(false)) {
					int[] model = problem.findModel();
					
					for (int i = 1; i <model.length; i++){
						String featureName = this.getVarNameInDimacs(file, i);
						if (model[i] < 0){
							configuration.add("!" + featureName);
						} else {
							configuration.add(featureName);
						}
					}
					
					break;
				}
			} catch (ContradictionException e){
				System.out.println("Not possible to find configuration with at least " + j + " features disabled..");
			}
		}
		
		return configuration;
	}
	
	/***
	 * <p>在 clause 配置项为真的情况下，包含为真的配置项数目最少的配置组合。注意这里选取的是满足条件的第一个配置组合。</p>
	 * @param file CNF 文件路径
	 * @param clause 为真的配置项的索引号
	 * @return 满足约束的第一个配置组合
	 * @throws Exception
	 */
	public List<String> getMostDisabled(String file, int[] clause) throws Exception {
		List<String> configuration = new ArrayList<String>();
		ISolver solver = SolverFactory.newDefault(); // ISolver 对象
		Reader reader = new DimacsReader ( solver );
		
		IProblem problem = reader.parseInstance(file); // 建立 IProblem 对象
		
		for (int j = 1; j < problem.nVars(); j++){ // 遍历 IProblem 中所有配置项
			solver = SolverFactory.newDefault();
			reader = new DimacsReader (solver);
			
			problem = reader.parseInstance(file);
			
			VecInt v = new VecInt();
			for (int i = 1; i < problem.nVars(); i++){
				v.push(i);
			}
			
			try {
				solver.addAtMost(v, (j+1)); // 最多有 j+1 个配置项
				solver.addClause(new VecInt(clause));	// 让 clause 数组中的那个配置项为真
				
				if ( problem.isSatisfiable(false)) {
					int[] model = problem.findModel();
					
					for (int i = 1; i <model.length; i++){
						String featureName = this.getVarNameInDimacs(file, i);
						if (model[i] < 0){
							configuration.add("!" + featureName);
						} else {
							configuration.add(featureName);
						}
					}
					
					break;
				}
			} catch (ContradictionException e){
				System.out.println("Not possible to find configuration with at least " + j + " features disabled..");
			}
		}
		
		return configuration;
	}
	
	/***
	 * <p>获取 CNF 第 id 的配置项名称</p>
	 * @param dimacs CNF 文件
	 * @param id 配置项的索引号
	 * @return 配置项的名称
	 * @throws Exception
	 */
	public String getVarNameInDimacs(String dimacs, int id) throws Exception {
		FileInputStream fis = new FileInputStream(new File(dimacs));
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		String line = null;
		while ((line = br.readLine()) != null) {
			if (line.startsWith("c " + id)){
				br.close();
				return line.split(" ")[2];
			}
		}
	 
		br.close();
		return null;
	}
	
	/***
	 * <p>获取 CNF 约束文件中包含的配置项个数</p>
	 * @param file CNF 文件路径
	 * @return 配置项个数
	 * @throws Exception
	 */
	public int getNumberOfFeaturesInDimac(String file) throws Exception {
		int numberOfFeatures = 0;
		FileInputStream fis = new FileInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		String line = null;
		while ((line = br.readLine()) != null) {
			if (line.startsWith("p cnf")){
				numberOfFeatures = new Integer(line.split(" ")[2]); // cnf 文件的这一行的第 3 个元素是特征模型包含的节点个数，即配置项的个数
			}
		}
		br.close();
		return numberOfFeatures;
	}
	
}
