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

public class ConstrainedAllEnabledDisabledOnewise extends SamplingAlgorithm {

	public List<List<String>> getSamples(File file, File ca1file) throws Exception {
		List<String> directives = super.getDirectives(file);
		List<List<String>> samplings = new CoveringArraysUtils().getValidProducts(ca1file, directives);
		List<List<String>> configs = new ArrayList<List<String>>();
		
		List<String> mostEnabled = null;
		for (List<String> configuration : samplings){
			if (mostEnabled == null){
				mostEnabled = configuration;
			} else {
				if (this.getNumberOfMacrosEnabled(mostEnabled) < this.getNumberOfMacrosEnabled(configuration)){
					mostEnabled = configuration;
				}
			}
		}
		
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
		
		if (mostEnabled.size() == 0){
			for (String configuration : directives){
				mostEnabled.add(configuration);
			}
		}
		
		if (mostDisabled.size() == 0){
			for (String configuration : directives){
				mostEnabled.add("!" + configuration);
			}
		}
		
		
		FeatureModel fm = null;
		if (file.getAbsolutePath().contains("/linux/")){
			fm = FeatureExprLib.featureModelFactory().createFromDimacsFile_2Var("featureModel/linux.dimacs");
		} else if (file.getAbsolutePath().contains("/busybox/")) {
			fm = FeatureExprLib.featureModelFactory().createFromDimacsFile_2Var("featureModel/busybox.dimacs");
		}
		
		FeatureExpr expr = FeatureExprFactory.True();
		for (String c : mostEnabled){
			expr = expr.and(FeatureExprFactory.createDefinedExternal(c));
		}
		
		if (expr.isSatisfiable(fm)){
			configs.add(mostEnabled);
		} else {
			System.err.println("File: " + file.getAbsolutePath());
		}
		
		expr = FeatureExprFactory.True();
		for (String c : mostDisabled){
			expr = expr.and(FeatureExprFactory.createDefinedExternal(c));
		}
		
		if (expr.isSatisfiable(fm)){
			configs.add(mostDisabled);
		} else {
			System.err.println("File: " + file.getAbsolutePath());
		}
		
		
		return configs;
	}
	
	@Override
	public List<List<String>> getSamples(File file) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
