package edu.cmu.cs.lti.ark.fn.parsing;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.HashMap;

import edu.cmu.cs.lti.ark.fn.clusters.ScrapTest;
import edu.cmu.cs.lti.ark.fn.utils.BitOps;
import edu.cmu.cs.lti.ark.util.FileUtil;

public class CreateAlphabetKBest
{
	public static void main(String[] args)
	{	 
		FEFileName.feFilename = args[0];
		FEFileName.tagFilename =  args[1];
		FEFileName.eventFilename =  args[2];
		FEFileName.alphafilename = args[3];
		FEFileName.spanfilename = args[4];		
		
		boolean genAlpha=Boolean.parseBoolean(args[5]);
		if(genAlpha)
			System.out.println("Generating alphabet too...");
		FEFileName.useUnlabeledSpans=Boolean.parseBoolean(args[6]);
		if(FEFileName.useUnlabeledSpans)
		{
			System.out.println("Using spans gathered from unlabeled data too...");
			FEFileName.unlabeledSpans=ScrapTest.getSpansWithHeads(FEFileName.unlabeledMatchedSpansFile);
		}
		FEFileName.KBestParse = new Integer(args[7]);
		FEFileName.KBestParseDirectory = args[8];
		run(genAlpha);
	}
	
	public static void run(boolean genAlpha) {
		DataPrep dprep=new DataPrep();
		long time=System.currentTimeMillis();
		System.out.println("Reading alphabet...");
		if(genAlpha){
			DataPrep.featIndex=new HashMap<String,Integer>();
		}
		if(DataPrep.featIndex==null){
			DataPrep.readFeatureIndex(FEFileName.alphafilename);
		}
		DataPrep.genAlpha=genAlpha;
		System.out.println("Finished Reading alphabet..."+(System.currentTimeMillis()-time));
		BufferedOutputStream bos=new BufferedOutputStream(FileUtil.openOutFile(FEFileName.eventFilename));
		int fCount=0;
		time=System.currentTimeMillis();
		while( dprep.hasNext()){
			int [][][] datapoint=dprep.getTrainData();
			System.out.print(".");
			if(fCount%100==0){
				System.out.println(fCount);
			}
			for(int i=0;i<datapoint.length;i++){
				for(int j=0;j<datapoint[i].length;j++){
					for(int k=0;k<datapoint[i][j].length;k++){
						BitOps.writeInt(datapoint[i][j][k],bos);
					}
					BitOps.writeInt(-1,bos);
				}
				BitOps.writeInt(-1,bos);
			}
			fCount++;
		}
		BitOps.writeInt(-1,bos);
		try{
			bos.close();
		}catch(IOException ioe){
			System.out.println(ioe.getMessage());
		}
		System.out.println(System.currentTimeMillis()-time);
		if(genAlpha){
			DataPrep.writeFeatureIndex(FEFileName.alphafilename);
		}
	}
	
}