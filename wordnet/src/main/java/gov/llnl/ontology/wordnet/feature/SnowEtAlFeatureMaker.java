/*
 * Copyright (c) 2010, Lawrence Livermore National Security, LLC. Produced at
 * the Lawrence Livermore National Laboratory. Written by Keith Stevens,
 * kstevens@cs.ucla.edu OCEC-10-073 All rights reserved. 
 *
 * This file is part of the C-Cat package and is covered under the terms and
 * conditions therein.
 *
 * The C-Cat package is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation and distributed hereunder to you.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND NO REPRESENTATIONS OR WARRANTIES,
 * EXPRESS OR IMPLIED ARE MADE.  BY WAY OF EXAMPLE, BUT NOT LIMITATION, WE MAKE
 * NO REPRESENTATIONS OR WARRANTIES OF MERCHANT- ABILITY OR FITNESS FOR ANY
 * PARTICULAR PURPOSE OR THAT THE USE OF THE LICENSED SOFTWARE OR DOCUMENTATION
 * WILL NOT INFRINGE ANY THIRD PARTY PATENTS, COPYRIGHTS, TRADEMARKS OR OTHER
 * RIGHTS.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package gov.llnl.ontology.wordnet.feature;

import gov.llnl.ontology.wordnet.Lemma;
import gov.llnl.ontology.wordnet.OntologyReader;
import gov.llnl.ontology.wordnet.SynsetSimilarity;
import gov.llnl.ontology.wordnet.Synset;
import gov.llnl.ontology.wordnet.Synset.PartsOfSpeech;
import gov.llnl.ontology.wordnet.SynsetRelations;
import gov.llnl.ontology.wordnet.sim.HirstStOngeSimilarity;
import gov.llnl.ontology.wordnet.sim.LeacockChodorowScaledSimilarity;
import gov.llnl.ontology.wordnet.sim.PathSimilarity;
import gov.llnl.ontology.wordnet.sim.WuPalmerSimilarity;

import edu.ucla.sspace.vector.DenseVector;
import edu.ucla.sspace.vector.DoubleVector;
import edu.ucla.sspace.vector.VectorIO;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOError;
import java.io.IOException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * This {@link SynsetPairFeatureMaker} creates a subset of the features used by
 * the following paper:
 *
 *   </p style="font-family:Garamond, Georgia, serif">Rion Snow; Sushant
 *   Prakash; Daniel Jurafsky; Andrew Y. Ng. "Learning to merge word senses," in
 *   <i> Proceedings of the 2007 Joint Conference on Empirical Methods in
 *   Natural Language Processing and Computational Natural Language Learning</i>
 *
 * </p>
 *
 * This class uses only features that are based on the path's linking two {@link
 * Synset}s.  This is done because {@link Synset}s that are automatically added
 * to WordNet are likely to be missing information content, glosses, and links
 * besides Hypernymy and Hyponymy.  
 *
 * </p>
 *
 * The feature vectors generated by this class can be used to train a
 * classifier, if a predetermined clustering for all {@link Sysnet}s can be
 * provided.  When creating a set of test feature vectors, this set of
 * assignments does not need to be provided.
 *
 * </p>
 *
 * Classes which want to use a superset of these features can extends this class
 * and implement three helper methods.
 *
 * @author Keith Stevens
 */
public class SnowEtAlFeatureMaker implements SynsetPairFeatureMaker {

  /**
   * A link to the word net hierarchy being used.
   */
  private final OntologyReader wordnet;

  /**
   * A mapping from a {@link Synset}'s sense key to it's cluster of {@link
   * Synset}s.  This may be null.
   */
  private final Map<String, Set<String>> mergedSenseInfo;

  /**
   * Creates a new {@link SnowEtAlFeatureMaker}.  No sense clustering is used.
   */
  public SnowEtAlFeatureMaker(OntologyReader wordnet) {
    this(wordnet, null);
  }

  /**
   * Creates a new {@link SnowEtAlFeatureMaker}.  If {@link mergedInfoFilename}
   * is not null, the cluster assignments are used as class labels.
   */
  public SnowEtAlFeatureMaker(OntologyReader wordnet,
                              String mergedInfoFilename) {
    this.wordnet = wordnet;

    // Create the mapping for known sense clusterings if a file is provided.
    if (mergedInfoFilename != null) {
      mergedSenseInfo = new HashMap<String, Set<String>>();
      try {
        BufferedReader br = new BufferedReader(new FileReader(
              mergedInfoFilename));
        for (String line = null; (line = br.readLine()) != null; ) {
          Set<String> keys = new HashSet<String>();
          keys.addAll(Arrays.asList(line.split("\\s+")));
          for (String key : keys)
            mergedSenseInfo.put(key, keys);
        }
      } catch (IOException ioe) {
        throw new IOError(ioe);
      }
    } else 
      mergedSenseInfo = null;
  }

  /**
   * Returns the number of extra features.  
   */
  protected int numExtraFeatures() {
    return 0;
  }

  /**
   * Appends extra attribute labels.
   */
  protected void addExtraAttributes(List<String> attributeList) {
  }

  /**
   * Adds extra feature values to {@code featureVector}.  {@code index} is the
   * first index at which a feature can be stored.
   */
  protected void addExtraFeatures(Synset sense1, Synset sense2,
                                  DoubleVector featureVector, int index) {
  }

  /**
   * {@inheritDoc}
   */
  public List<String> makeAttributeList() {
    List<String> attributeList = new ArrayList<String>();
    attributeList.add("HSO");
    attributeList.add("LCH");
    attributeList.add("WUP");
    attributeList.add("PATH");
    attributeList.add("MN");
    attributeList.add("MAXMN");
    attributeList.add("SENSECOUNT");

    addExtraAttributes(attributeList);

    attributeList.add("class");

    return attributeList;
  }

  /**
   * {@inheritDoc}
   */
  public DoubleVector makeFeatureVector(Synset sense1, Synset sense2) {
    PartsOfSpeech pos = sense1.getPartOfSpeech();

    DoubleVector values = new DenseVector(8 + numExtraFeatures());

    // Add the class label.  1 for merged according to some known scheme
    // or 0 otherwise.
    values.set(values.length() - 1, 0);
    if (mergedSenseInfo != null) {
      Set<String> senseKeys = mergedSenseInfo.get(sense1.getName());

      // If we have a data point that lacks sense clustering info when it is
      // required, drop the data point.
      if (senseKeys == null)
        return null;

      if (senseKeys.contains(sense2.getName()))
        values.set(values.length() - 1, 1);
    }

    SynsetSimilarity[] simFunctions = new SynsetSimilarity[] {
        new HirstStOngeSimilarity(),
        new LeacockChodorowScaledSimilarity(wordnet),
        new WuPalmerSimilarity(),
        new PathSimilarity()
    };

    // Set the pure path based similarity features.
    for (int i = 0; i < simFunctions.length; ++i)
        values.set(i, simFunctions[i].similarity(sense1, sense2));

    // Set the MN and MAXMN features.
    Synset lowestCH = SynsetRelations.lowestCommonHypernym(
        sense1, sense2);
    if (lowestCH != null) {
      values.set(4, Math.min(
          SynsetRelations.shortestPathDistance(sense1, lowestCH),
          SynsetRelations.shortestPathDistance(sense2, lowestCH)));
      values.set(5, Math.min(
          SynsetRelations.longestPathDistance(sense1, lowestCH),
          SynsetRelations.longestPathDistance(sense2, lowestCH)));
    }

    // Add the SENSECOUNT feature.
    Set<Lemma> sharedLemmas = new HashSet<Lemma>();
    sharedLemmas.addAll(sense1.getLemmas());
    int maxSenseForLemma = 0;
    for (Lemma sharedLemma : sense2.getLemmas())
      if (sharedLemmas.contains(sharedLemma))
        maxSenseForLemma = Math.max(
            maxSenseForLemma, 
            wordnet.getSynsets(sharedLemma.getLemmaName(), pos).length);
    values.set(6, maxSenseForLemma);

    // Add any extra feautres.
    addExtraFeatures(sense1, sense2, values, 7);

    return values;
  }

  public String toString() {
    return "SnowEtAlFeatureMaker";
  }
}
