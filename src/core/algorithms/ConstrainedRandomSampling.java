package core.algorithms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import core.SamplingAlgorithm;
import de.fosd.typechef.featureexpr.FeatureExpr;
import de.fosd.typechef.featureexpr.FeatureExprFactory;
import de.fosd.typechef.featureexpr.FeatureModel;
import de.fosd.typechef.lexer.FeatureExprLib;

public class ConstrainedRandomSampling extends SamplingAlgorithm{

	public static int NUMBER_CONFIGS = 0; 
	
	@Override
	public List<List<String>> getSamples(File file) throws Exception {
		List<List<String>> configurations = new ArrayList<>();
		directives = this.getDirectives(file);
//		System.out.println("Directives: " + directives);
		if (directives.size() > 0){
		
			for (int j = 0; j < ConstrainedRandomSampling.NUMBER_CONFIGS; j++){
				// It set or not-set each configuration..
				List<String> configuration = new ArrayList<>();
				for (int i = 0; i < (directives.size()); i++){
					if (this.getRandomBoolean()){
						configuration.add(directives.get(i));
					} else {
						configuration.add("!" + directives.get(i));
					}
				}
				
				FeatureModel fm = null;
//				if (file.getAbsolutePath().contains("/linux/")){ // BUG1: we should consider the Windows platform
				if (file.getAbsolutePath().contains("/linux/") || file.getAbsolutePath().contains("\\linux\\")){
					fm = FeatureExprLib.featureModelFactory().createFromDimacsFile_2Var("featureModel/linux.dimacs");
//				} else if (file.getAbsolutePath().contains("/busybox/")){ // BUG1: we should consider the Windows platform
				} else if (file.getAbsolutePath().contains("/busybox/") || file.getAbsolutePath().contains("\\busybox\\")){
					fm = FeatureExprLib.featureModelFactory().createFromDimacsFile_2Var("featureModel/busybox.dimacs");
				}
				
				FeatureExpr expr = FeatureExprFactory.True(); // ���� configuration ���������������ɱ��ʽ
				for (String config : configuration){
					expr = expr.and(FeatureExprFactory.createDefinedExternal(config));
				}
				if (expr.isSatisfiable(fm)){ // ������ɵı��ʽ�Ƿ����� CNF ����
					if (!configurations.contains(configuration)){
						configurations.add(configuration);
					}
				}
				
			}
		
		} else {
			configurations.add(new ArrayList<String>());
		}
		
		// It gets each configuration and adds an #UNDEF for the macros that are not active..
		// ��� C �ļ�����������������ɵ���������У�����Щ���������
		for (List<String> configuration : configurations){
			for (String directive : directives){
				if (!configuration.contains(directive) && !configuration.contains("!" + directive)){
					configuration.add("!" + directive);
				}
			}
		}
		
		return configurations;
	}

	public boolean getRandomBoolean() {
	    Random random = new Random();
	    return random.nextBoolean();
	}
	
	/***
	 * <p> ��� main ���������Լ���ӵģ�Ϊ�˲��� ConstraintsRandomSampling �����ܺ���������</p>
	 * @param args
	 */
	public static void main(String[] args){
		try {
			ConstrainedRandomSampling crs = new ConstrainedRandomSampling();
			crs.NUMBER_CONFIGS = 5;
			List<List<String>> configs = crs.getSamples(new File("bugs/linux/kernel/sched/proc.c"));
			for(List<String> config: configs){
				System.out.println(config);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
