package core.algorithms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import core.CoveringArraysUtils;
import core.SamplingAlgorithm;
import de.fosd.typechef.featureexpr.FeatureExpr;
import de.fosd.typechef.featureexpr.FeatureExprFactory;
import de.fosd.typechef.featureexpr.FeatureModel;
import de.fosd.typechef.lexer.FeatureExprLib;

public class ConstrainedOneEnabledOnewise extends SamplingAlgorithm {

	public List<List<String>> getSamples(File file, File ca1file) throws Exception {
		List<List<String>> configs = new ArrayList<List<String>>();
		List<String> directives = super.getDirectives(file);
		
		FeatureModel fm = null;
		if (file.getAbsolutePath().contains("/busybox/")){
			fm = FeatureExprLib.featureModelFactory().createFromDimacsFile_2Var("featureModel/busybox.dimacs");
		} else {
			fm = FeatureExprLib.featureModelFactory().createFromDimacsFile_2Var("featureModel/linux.dimacs");
		}
		
		
		List<List<String>> samplings = new CoveringArraysUtils().getValidProducts(ca1file, directives);
		List<String> mostDisabled = null;
		for (List<String> configuration : samplings){
			if (mostDisabled == null){
				mostDisabled = configuration;
			} else {
				if (this.getNumberOfMacrosEnabled(mostDisabled) > this.getNumberOfMacrosEnabled(configuration)){
					mostDisabled = configuration;
				}
			}
		}
		
		for (int i = 0; i < mostDisabled.size(); i++){
			String toEnable = mostDisabled.get(i);
			List<String> config = new ArrayList<String>();
			for (String directive : mostDisabled){
				if (!directive.equals(toEnable)){
					config.add(directive);
				} else {
					directive = directive.replace("!", "");
					config.add(directive);
				}
			}
			
			FeatureExpr expr = FeatureExprFactory.True();
			for (String c : config){
				expr = expr.and(FeatureExprFactory.createDefinedExternal(c));
			}
			
			if (expr.isSatisfiable(fm)){
				if (!configs.contains(config)){
					configs.add(config);
				}
			} else {
				System.err.println("File: " + file.getAbsolutePath());
			}
		}
		
		
		// It gets each configuration and adds an #UNDEF for the macros that are not active..
		for (List<String> configuration : configs){
			for (String directive : directives){
				if (!configuration.contains(directive) && !configuration.contains("!" + directive)){
					configuration.add("!" + directive);
				}
			}
		}
		
		if (configs.size() == 0){
			configs.add(new ArrayList<String>());
		}
		return configs;
	}
	
	@Override
	public List<List<String>> getSamples(File file) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
