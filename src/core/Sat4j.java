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
 * <p>Sat4j �������� sat4j �⣬�ṩ�˴�Լ���� One-enabled ({@link#getOneEnabled})��One-disabled ({@link#getOneDisabled}) �ķ���ʵ�֡�</p>
 * <p>sat4j �������Ҫ��������ģ�͵� CNF �ļ����Ը�Լ���ļ���Ϊ���룬���������������������Ϊ�����</p>
 * @editor yongfeng
 */
public class Sat4j {

	/***
	 * <p>��� OneEnabled �㷨����� SAT ������Ӵ����� CNF Լ���ģ�Ϊ�����ٵ�������ϡ�ע��ÿһ��������ᱻΪ��һ�Ρ�</p>
	 * @param file CNF �ļ�
	 * @return ��������
	 * @throws Exception
	 */
	public List<List<String>> getOneEnabled(String file) throws Exception {
		List<List<String>> configurations = new ArrayList<List<String>>();
		for (int i = 1; i < getNumberOfFeaturesInDimac(file); i++){ // CNF ���� size ��������
			int[] clause = {i};
			List<String> configuration = this.getMostDisabled(file, clause); // ���Լ�������£�disabled �����������
			configurations.add(configuration);
		}	
		
		return configurations;
	}
	
	/***
	 * <p>��� Disabled �㷨����� SAT ������Ӵ����� CNF Լ���ģ�Ϊ�����ٵ�������ϡ�ע��ÿһ��������ᱻΪ��һ�Ρ�</p>
	 * @param file CNF �ļ�
	 * @return ��������
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
	 * <p>�� clause ������Ϊ�������£�����Ϊ�����������Ŀ���ٵ�������ϡ�ע������ѡȡ�������������ĵ�һ��������ϡ�</p>
	 * @param file CNF �ļ�·��
	 * @param clause Ϊ����������������
	 * @return ����Լ���ĵ�һ���������
	 * @throws Exception
	 */
	public List<String> getMostDisabled(String file, int[] clause) throws Exception {
		List<String> configuration = new ArrayList<String>();
		ISolver solver = SolverFactory.newDefault(); // ISolver ����
		Reader reader = new DimacsReader ( solver );
		
		IProblem problem = reader.parseInstance(file); // ���� IProblem ����
		
		for (int j = 1; j < problem.nVars(); j++){ // ���� IProblem ������������
			solver = SolverFactory.newDefault();
			reader = new DimacsReader (solver);
			
			problem = reader.parseInstance(file);
			
			VecInt v = new VecInt();
			for (int i = 1; i < problem.nVars(); i++){
				v.push(i);
			}
			
			try {
				solver.addAtMost(v, (j+1)); // ����� j+1 ��������
				solver.addClause(new VecInt(clause));	// �� clause �����е��Ǹ�������Ϊ��
				
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
	 * <p>��ȡ CNF �� id ������������</p>
	 * @param dimacs CNF �ļ�
	 * @param id �������������
	 * @return �����������
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
	 * <p>��ȡ CNF Լ���ļ��а��������������</p>
	 * @param file CNF �ļ�·��
	 * @return ���������
	 * @throws Exception
	 */
	public int getNumberOfFeaturesInDimac(String file) throws Exception {
		int numberOfFeatures = 0;
		FileInputStream fis = new FileInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		String line = null;
		while ((line = br.readLine()) != null) {
			if (line.startsWith("p cnf")){
				numberOfFeatures = new Integer(line.split(" ")[2]); // cnf �ļ�����һ�еĵ� 3 ��Ԫ��������ģ�Ͱ����Ľڵ��������������ĸ���
			}
		}
		br.close();
		return numberOfFeatures;
	}
	
}
