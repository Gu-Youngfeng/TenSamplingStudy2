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
				
				FeatureExpr expr = FeatureExprFactory.True(); // 根据 configuration 包含的配置项生成表达式
				for (String config : configuration){
					expr = expr.and(FeatureExprFactory.createDefinedExternal(config));
				}
				if (expr.isSatisfiable(fm)){ // 检查生成的表达式是否满足 CNF 条件
					if (!configurations.contains(configuration)){
						configurations.add(configuration);
					}
				}
				
			}
		
		} else {
			configurations.add(new ArrayList<String>());
		}
		
		// It gets each configuration and adds an #UNDEF for the macros that are not active..
		// 如果 C 文件包含的配置项不在生成的配置组合中，则将这些配置项禁用
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
	 * <p> 这个 main 函数是我自己添加的，为了测试 ConstraintsRandomSampling 的性能和输出结果。</p>
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
