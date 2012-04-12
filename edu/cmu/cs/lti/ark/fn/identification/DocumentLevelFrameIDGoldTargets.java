/*******************************************************************************
 * Copyright (c) 2011 Dipanjan Das 
 * Language Technologies Institute, 
 * Carnegie Mellon University, 
 * All Rights Reserved.
 * 
 * FrameIdentificationGoldTargets.java is part of SEMAFOR 2.0.
 * 
 * SEMAFOR 2.0 is free software: you can redistribute it and/or modify  it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * SEMAFOR 2.0 is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. 
 * 
 * You should have received a copy of the GNU General Public License along
 * with SEMAFOR 2.0.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package edu.cmu.cs.lti.ark.fn.identification;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;


import edu.cmu.cs.lti.ark.fn.data.prep.ParsePreparation;
import edu.cmu.cs.lti.ark.fn.utils.FNModelOptions;
import edu.cmu.cs.lti.ark.util.SerializedObjects;
import edu.cmu.cs.lti.ark.util.nlp.parse.DependencyParse;
import edu.cmu.cs.lti.ark.util.optimization.LDouble;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectDoubleHashMap;

/*
 * This does not work if start and end are not all the lines in the 
 * input file. 
 * TODO: change this
 */
public class DocumentLevelFrameIDGoldTargets {
	public static void main(String[] args) {
		FNModelOptions options = new FNModelOptions(args);
		ArrayList<String> parses = new ArrayList<String>();
		int start = options.startIndex.get();
		int end = options.endIndex.get();
		int count = 0;
		ArrayList<String> tokenNums = new ArrayList<String>();
		ArrayList<String> orgSentenceLines = new ArrayList<String>();
		ArrayList<String> originalIndices = new ArrayList<String>();
		try {
			BufferedReader inParses = new BufferedReader(new FileReader(options.testParseFile.get()));
			BufferedReader inOrgSentences = new BufferedReader(new FileReader(options.testTokenizedFile.get()));
			String line = null;
			int dummy = 0;
			while((line=inParses.readLine())!=null) {
				String line2 = inOrgSentences.readLine().trim();
				if(count<start)	{
					count++;
					continue;
				}
				parses.add(line.trim());
				orgSentenceLines.add(line2);
				tokenNums.add(""+dummy);	// ?? I think 'dummy' is just the offset of the sentence relative to options.startIndex
				originalIndices.add(""+count);
				if(count==(end-1))	// skip sentences after the specified range
					break;
				count++;
				dummy++;
			}				
			inParses.close();
			inOrgSentences.close();
		} catch(Exception e) {
			e.printStackTrace();
		}		
		RequiredDataForFrameIdentification r = (RequiredDataForFrameIdentification)SerializedObjects.readSerializedObject(options.fnIdReqDataFile.get());
		THashSet<String> allRelatedWords = r.getAllRelatedWords();
		Map<String, Set<String>> relatedWordsForWord = r.getRelatedWordsForWord();
		THashMap<String,THashSet<String>> frameMap = r.getFrameMap();
		THashMap<String,THashSet<String>> cMap = r.getcMap();
		Map<String, Map<String, Set<String>>> revisedRelationsMap = 
			r.getRevisedRelMap();
		
		ArrayList<String> segs = ParsePreparation.readSentencesFromFile(options.testFrameFile.get());
		ArrayList<String> inputForFrameId = getGoldSeg(segs,start,end);	// Null\tTargetTokenNum(s)\tSentenceOffset
		ArrayList<String> idResult = new ArrayList<String>();
		TObjectDoubleHashMap<String> paramList = parseParamFile(options.idParamFile.get());
		Map<String, String> hvLemmas = r.getHvLemmaCache();
		boolean nohv = options.noHV.get().equals("nohv");
		FastFrameIdentifier idModel = new FastFrameIdentifier(
				paramList, 
				"reg", 
				0.0, 
				frameMap, 
				null, 
				cMap,
				relatedWordsForWord,
				revisedRelationsMap,
				hvLemmas,
				nohv);
		System.out.println("Size of originalSentences list:"+originalIndices.size());
		
		boolean useClusters = options.clusterFeats.get().equals("true");
		if(useClusters) {
			System.out.println("Using cluster based features...");
			THashMap<String, THashSet<String>> clusterMap= (THashMap<String, THashSet<String>>)SerializedObjects.readSerializedObject(options.synClusterMap.get());
			int K = options.clusterK.get();
			idModel.setClusterInfo(clusterMap,K);
		}
		
		boolean usegraph = !options.useGraph.get().equals("null");
		SmoothedGraph sg = null;
		if (usegraph) {
			sg = (SmoothedGraph)SerializedObjects.readSerializedObject(options.useGraph.get());
		}
		String startEndFile = options.startEndFile.get();
		getPredicateGroups(inputForFrameId, startEndFile, parses);
		
		System.out.println("Start Time:"+(new Date()));
		for(String input: inputForFrameId) {
			String[] toks = input.split("\t");
			int sentNum = new Integer(toks[2]);	// offset of the sentence within the loaded data (relative to options.startIndex)
			String bestFrame = null;
			if (sg == null) {
				bestFrame = idModel.getBestFrame(input,parses.get(sentNum));
			} else {
				bestFrame = idModel.getBestFrame(input,parses.get(sentNum), sg);
			}
			String tokenRepresentation = getTokenRepresentation(toks[1],parses.get(sentNum));  
			String[] split = tokenRepresentation.trim().split("\t");
			String sentCount = originalIndices.get(sentNum);
			idResult.add(1+"\t"+bestFrame+"\t"+split[0]+"\t"+toks[1]+"\t"+split[1]+"\t"+sentCount);	// BestFrame\tTargetTokenNum(s)\tSentenceOffset
			System.out.println("1"+"\t"+bestFrame+"\t"+split[0]+"\t"+toks[1]+"\t"+split[1]+"\t"+sentCount);
		}
		System.out.println("End Time:"+(new Date()));
		String feFile = options.frameElementsOutputFile.get();
		ParsePreparation.writeSentencesToTempFile(feFile, idResult);	
	}	
	
	public static void getPredicateGroups(ArrayList<String> inputForFrameId,
			                              String startEndFile,
			                              ArrayList<String> parses) {
		ArrayList<String> startsAndEnds = ParsePreparation.readSentencesFromFile(startEndFile);
		int size = startsAndEnds.size();
		int[][] ses = new int[size][];
		for (int i = 0; i < size; i++) {
			String[] toks = startsAndEnds.get(i).split("\t");
			ses[i] = new int[2];
			ses[i][0] = new Integer(toks[0]);
			ses[i][1] = new Integer(toks[1]);
		}
		
		size = inputForFrameId.size();
		for (int i = 0; i < size; i++) {
			String frameLine = inputForFrameId.get(i);
			String[] toks = frameLine.split("\t");
			int sentNum = new Integer(toks[2]);
			int group = getGroup(ses, sentNum);
			
			String parseLine = parses.get(sentNum);
			String[] tokNums = toks[1].split("_");
			int[] intTokNums = new int[tokNums.length];
			for(int j = 0; j < tokNums.length; j ++)
				intTokNums[j] = new Integer(tokNums[j]);
			Arrays.sort(intTokNums);
			StringTokenizer st = new StringTokenizer(parseLine,"\t");
			int tokensInFirstSent = new Integer(st.nextToken());
			String[][] data = new String[6][tokensInFirstSent];
			for(int k = 0; k < 6; k ++)
			{
				data[k]=new String[tokensInFirstSent];
				for(int j = 0; j < tokensInFirstSent; j ++)
				{
					data[k][j]=""+st.nextToken().trim();
				}
			}	
			String lemmatizedTokens = "";
			for(int j = 0; j < intTokNums.length; j ++)
			{
				lemmatizedTokens+=data[5][intTokNums[j]]+" ";
			}
			lemmatizedTokens=lemmatizedTokens.trim();
			DependencyParse parse = DependencyParse.processFN(data, 0.0);
			DependencyParse[] sortedNodes = DependencyParse.getIndexSortedListOfNodes(parse);
			DependencyParse head = DependencyParse.getHeuristicHead(sortedNodes, intTokNums);
			String pos = head.getPOS();
			if (pos.startsWith("N")) {
				pos = "n";
			} else if (pos.startsWith("V")) {
				pos = "v";
			} else if (pos.startsWith("J")) {
				pos = "a";
			} else if (pos.startsWith("RB")) {
				pos = "adv";
			} else if (pos.startsWith("I") || pos.startsWith("TO")) {
				pos = "prep";
			} else {
				pos = null;
			}
			if (pos == null) {
				System.out.println("Problem. Cannot handle null POS. Exiting.");
				System.exit(-1);
			}
			String predicate = lemmatizedTokens + "." + pos;
			
		}
	}
	
	public static int getGroup(int[][] ses, int sentNum) {
		for (int i = 0; i < ses.length; i++) {
			if (sentNum >= ses[i][0] && sentNum < ses[i][1]) {
				return i;
			}
		}
		System.out.println("Problem. Could not find span.");
		System.exit(-1);
		return ses.length;
	}
	
	public static TObjectDoubleHashMap<String> parseParamFile(String paramsFile)
	{
		TObjectDoubleHashMap<String> startParamList = new TObjectDoubleHashMap<String>(); 
		try {
			BufferedReader fis = new BufferedReader(new FileReader(paramsFile));
			String pattern = null;
			int count = 0;
			while ((pattern = fis.readLine()) != null)
			{
				StringTokenizer st = new StringTokenizer(pattern.trim(),"\t");
				String paramName = st.nextToken().trim();
				String rest = st.nextToken().trim();
				String[] arr = rest.split(",");
				double value = new Double(arr[0].trim());
				boolean sign = new Boolean(arr[1].trim());
				LDouble val = new LDouble(value,sign);
				startParamList.put(paramName, val.exponentiate());
				if(count%100000==0)
					System.out.println("Processed param number:"+count);
				count++;
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return startParamList;
	}
	
	public static ArrayList<String> getGoldSeg(ArrayList<String> segs,int start, int end)
	{
		ArrayList<String> result = new ArrayList<String>();
		int count = 0;
		for(String seg:segs)
		{
			String[] toks = seg.split("\t");
			int sentNum = new Integer(toks[5]);
			if(sentNum<start)
			{
				count++;
				continue;
			}
			if(sentNum>=end)
				break;
			String span = toks[3];
			String line = "Null\t"+span+"\t"+(sentNum-start);
			result.add(line);
			count++;
		}
		return result;
	}
	
	public static String getTokenRepresentation(String tokNum, String parse)
	{
		StringTokenizer st = new StringTokenizer(parse,"\t");
		int tokensInFirstSent = new Integer(st.nextToken());
		String[][] data = new String[5][tokensInFirstSent];
		for(int k = 0; k < 5; k ++)
		{
			data[k]=new String[tokensInFirstSent];
			for(int j = 0; j < tokensInFirstSent; j ++)
			{
				data[k][j]=""+st.nextToken().trim();
			}
		}
		String[] tokNums = tokNum.split("_");
		int[] intTokNums = new int[tokNums.length];
		for(int j = 0; j < tokNums.length; j ++)
			intTokNums[j] = new Integer(tokNums[j]);
		Arrays.sort(intTokNums);
		
		String actualTokens = "";
		String firstTok = "";
		for(int i = 0; i < intTokNums.length; i ++)
		{
			String lexUnit = data[0][intTokNums[i]];
			String pos = data[1][intTokNums[i]];	
			actualTokens+=lexUnit+" ";
			if(i==0)
				firstTok =  lexUnit.toLowerCase()+"."+pos.substring(0,1).toLowerCase();
		}
		actualTokens=actualTokens.trim();
		firstTok=firstTok.trim();
		
		return firstTok+"\t"+actualTokens;
	}
}