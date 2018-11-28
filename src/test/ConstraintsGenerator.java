package test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class ConstraintsGenerator {

	public static void main(String[] args) throws Exception {
		PrintWriter writer = new PrintWriter("constraints.txt");
		
		
		System.out.println(279657);
		
		FileInputStream fis = new FileInputStream("linux-constraints.txt");
		 
		//Construct BufferedReader from InputStreamReader
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		int count = 1;
		String line = null;
		while ((line = br.readLine()) != null) {
			System.out.println(count++);
			String[] parts = line.split(" ");
			writer.println(parts.length-1);
			for (int i = 0; i < parts.length-1; i++){
				int aux = new Integer(parts[i]);
				if (aux < 0){
					aux = (-1) * aux;
					aux = (aux*2);
					aux = aux - 1;
					aux = (-1) * aux;
				} else {
					aux = (aux*2);
					aux = aux - 1;
				}
				writer.print(aux + " ");
			}
			writer.println();
		}
	 
		br.close();
		writer.close();
	}
	
}
