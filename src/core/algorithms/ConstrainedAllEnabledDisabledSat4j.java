package core.algorithms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import util.InvalidConfigException;
import core.SamplingAlgorithm;
import core.Sat4j;
import de.fosd.typechef.featureexpr.FeatureExpr;
import de.fosd.typechef.featureexpr.FeatureExprFactory;
import de.fosd.typechef.featureexpr.FeatureModel;
import de.fosd.typechef.lexer.FeatureExprLib;

public class ConstrainedAllEnabledDisabledSat4j extends SamplingAlgorithm {

	public List<List<String>> getSamples(File srcFile, File dimacsFile) throws Exception{
		List<String> directives = super.getDirectives(srcFile);
		
		List<String> mostEnabled = new Sat4j().getMostEnabled(dimacsFile.getAbsolutePath());
		List<String> mostDisabled = new Sat4j().getMostDisabled(dimacsFile.getAbsolutePath());
		
		List<String> mostEnabledFinal = new ArrayList<String>();
		for (String directive : mostEnabled){
			String directiveAux = directive.replace("!", "");
			if (directives.contains(directiveAux)){
				mostEnabledFinal.add(directive);
			}
		}
		
		List<String> mostDisabledFinal = new ArrayList<String>();
		for (String directive : mostDisabled){
			String directiveAux = directive.replace("!", "");
			if (directives.contains(directiveAux)){
				mostDisabledFinal.add(directive);
			}
		}
		
		List<List<String>> configurations = new ArrayList<List<String>>();
		configurations.add(mostDisabledFinal);
		configurations.add(mostEnabledFinal);
		
		FeatureModel fm = FeatureExprLib.featureModelFactory().createFromDimacsFile_2Var(dimacsFile.getAbsolutePath());
		
		
		FeatureExpr expr = FeatureExprFactory.True();
		for (String c : mostEnabled){
			expr = expr.and(FeatureExprFactory.createDefinedExternal(c));
		}
		if (expr.isSatisfiable(fm)){
			System.out.println(srcFile.getAbsolutePath());
		} else {
			throw new InvalidConfigException();
		}
		
		expr = FeatureExprFactory.True();
		for (String c : mostDisabled){
			expr = expr.and(FeatureExprFactory.createDefinedExternal(c));
		}
		if (expr.isSatisfiable(fm)){
			System.out.println(srcFile.getAbsolutePath());
		} else {
			throw new InvalidConfigException();
		}
		
		return configurations;
	}
	
	@Override
	public List<List<String>> getSamples(File file) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
