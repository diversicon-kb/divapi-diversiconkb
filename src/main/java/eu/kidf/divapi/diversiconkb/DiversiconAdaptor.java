package eu.kidf.divapi.diversiconkb;

import de.tudarmstadt.ukp.lmf.model.core.LexicalEntry;
import de.tudarmstadt.ukp.lmf.model.core.Lexicon;
import de.tudarmstadt.ukp.lmf.model.core.Sense;
import de.tudarmstadt.ukp.lmf.model.enums.ERelNameSemantics;
import de.tudarmstadt.ukp.lmf.model.semantics.SenseRelation;
import de.tudarmstadt.ukp.lmf.model.semantics.Synset;
import de.tudarmstadt.ukp.lmf.model.semantics.SynsetRelation;
import eu.kidf.divapi.Concept;
import eu.kidf.divapi.Domain;
import eu.kidf.divapi.IDivAPI;
import eu.kidf.diversicon.core.DivConfig;
import eu.kidf.diversicon.core.Diversicon;
import eu.kidf.diversicon.core.Diversicons;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Adaptor implementing the DivAPI interface for accessing the Diversicon KB.
 * 
 * @author Gábor BELLA
 * @author <a rel="author" href="http://davidleoni.it/">David Leoni</a>
 */
public class DiversiconAdaptor implements IDivAPI {
    
    private Diversicon dic;
    private Double threshold;
    
    public DiversiconAdaptor(String pathToResource) throws IOException {
        try {
            dic = Diversicon.connectToDb(DivConfig.of(Diversicons.h2FileConfig(pathToResource, true)));
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public Set<String> getRelatedWords(String language, Domain domain, String word, WordRelation rel) {
        Set<String> relatedWords = new HashSet<>();
        if (word == null) {
            return relatedWords;
        }
        List<String> ubySenseRels = new ArrayList<>();
        List<String> ubySynsetRels = new ArrayList<>();
        if (rel.equals(IDivAPI.WORD_ANTONYMY)) {
            ubySenseRels.add(ERelNameSemantics.ANTONYM);
        } else if (rel.equals(IDivAPI.WORD_SYNONYMY)) {
            ubySenseRels.add(ERelNameSemantics.SYNONYM);
        } else if (rel.equals(IDivAPI.WORD_SIMILARITY)) {
            ubySenseRels.add(ERelNameSemantics.SYNONYMNEAR);
            ubySynsetRels.add(ERelNameSemantics.HYPERNYM);
            ubySynsetRels.add(ERelNameSemantics.HYPERNYMINSTANCE);
            ubySynsetRels.add(ERelNameSemantics.HYPONYM);
            ubySynsetRels.add(ERelNameSemantics.HYPONYMINSTANCE);
        } else {
            ubySynsetRels.add(ERelNameSemantics.CAUSEDBY);
            ubySynsetRels.add(ERelNameSemantics.CAUSES);
            ubySynsetRels.add(ERelNameSemantics.ENTAILEDBY);
            ubySynsetRels.add(ERelNameSemantics.ENTAILS);
            ubySynsetRels.add(ERelNameSemantics.HOLONYM);
            ubySynsetRels.add(ERelNameSemantics.MERONYM);
            ubySenseRels.add(ERelNameSemantics.RELATED);
            ubySenseRels.add(ERelNameSemantics.SEEALSO);
        }

        Set<Concept> concepts = getConcepts(language, domain, word);
        for (Concept concept : concepts) {
            Synset synset = (Synset) concept.getContainer();
            if (!ubySenseRels.isEmpty()) {
                // look up sense relations
                List<Sense> senses = synset.getSenses();
                for (Sense sense : senses) {
                    if (ubySenseRels.contains(ERelNameSemantics.SYNONYM)) {
                        // compute synonyms
                        String w = sense.getLexicalEntry().getLemmaForm();
                        if (!w.equals(word)) {
                            relatedWords.add(w);
                        }
                    } else {
                        // retrieve words related by sense relations
                        List<SenseRelation> senseRels = sense.getSenseRelations();
                        for (SenseRelation sr : senseRels) {
                            if (ubySenseRels.contains(sr.getRelName())) {
                                String w = sr.getTarget().getLexicalEntry().getLemmaForm();
                                if (!w.equals(word)) {
                                    relatedWords.add(w);
                                }
                            }
                        }
                    }
                }
            }
            if (!ubySynsetRels.isEmpty()) {
                // look up synset relations
                List<SynsetRelation> synRels = synset.getSynsetRelations();
                System.out.println("call");
                Set<Synset> relatedSynsets = dic.getConnectedSynsets(concept.getID(), 3, ubySynsetRels);
                System.out.println("finished " + relatedSynsets.size());
                for (Synset relSyn : relatedSynsets) {
                    List<Sense> relSenses = relSyn.getSenses();
                    for (Sense relSense : relSenses) {
                        String w = relSense.getLexicalEntry().getLemmaForm();
                        if (!w.equals(word)) {
                            relatedWords.add(w);
                        }
                    }
                }
            }
        }
        return relatedWords;
    }

    @Override
    public Map<String, Double> getRelatedWordsWeighted(String language, Domain domain, String word, WordRelation rel) {
        Map<String, Double> wordMap = new HashMap<>();
        Set<String> wordSet = getRelatedWords(language, domain, word, rel);
        for (String w : wordSet) {
            wordMap.put(w, 1.0);
        }
        return wordMap;
    }

    @Override
    public Set<WordRelation> getRelations(String language, Domain domain, String word1, String word2) {
        Set<ConceptRelation> relSet = new HashSet<>();
        Set<WordRelation> outputSet = new HashSet<>();
        Set<String> ubyRelSet = new HashSet<>();
        Set<Concept> concepts1 = getConcepts(language, domain, word1);
        Set<Concept> concepts2 = getConcepts(language, domain, word2);
        for (Concept concept1 : concepts1) {
            for (Concept concept2 : concepts2) {
                if (concept1.getID().equals(concept2.getID())) {
                    ubyRelSet.add(ERelNameSemantics.SYNONYM);
                } else {
                    ubyRelSet.addAll(dic.getRelations(concept1.getID(), concept2.getID(), -1));
                }
            }
        }
        if (ubyRelSet.isEmpty()) {
            return outputSet;
        }
        for (String relName : ubyRelSet) {
            relSet.add(convertRelationName(relName));
        }
        if (relSet.contains(IDivAPI.CONCEPT_EQUIVALENCE)) {
            outputSet.add(IDivAPI.WORD_SYNONYMY);
            return outputSet;
        } 
        if (relSet.contains(IDivAPI.CONCEPT_HYPERNYMY) || relSet.contains(IDivAPI.CONCEPT_HYPONYMY) ||
            relSet.contains(IDivAPI.CONCEPT_SIMILARITY)) {
            outputSet.add(IDivAPI.WORD_SIMILARITY);
            return outputSet;
        }
        outputSet.add(IDivAPI.WORD_RELATEDNESS);
        return outputSet;
    }

    @Override
    public Map<WordRelation, Double> getRelationsWeighted(String language, Domain domain, String word1, String word2) {
        Map<WordRelation, Double> relMap = new HashMap<>();
        Set<WordRelation> wrs = getRelations(language, domain, word1, word2);
        for (WordRelation wr : wrs) {
            if (wr != null) {
                relMap.put(wr, 1.0);
            }
        }
        return relMap;
    }

    @Override
    public Set<String> getLanguages(Domain domain, String word) {
        Set<String> langList = new HashSet<>();
        List<Lexicon> lexicons = dic.getLexicons();
        for (Lexicon l : lexicons) {
            if (dic.getLexicalEntries(word, l).size() > 0) {
                langList.add(l.getLanguageIdentifier());
            }
        }
        return langList;
    }

    @Override
    public Set<Domain> getDomains(String language, String word) {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

    @Override
    public Map<Domain, Double> getDomainsWeighted(String language, String word, Set<Domain> domains) {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

    @Override
    public Set<Concept> getConcepts(String language, Domain domain, String word) {
        Set<Concept> result = new HashSet<>();
        List<LexicalEntry> lexEntries = dic.getLexicalEntries(word, null);
        for (LexicalEntry lexEntry : lexEntries) {
            List<Synset> synsets = lexEntry.getSynsets();
            for (Synset synset : synsets) {
                Concept c = new Concept(synset, synset.getId());
                result.add(c);
            }
        }
        return result;
    }

    @Override
    public Map<Concept, Double> getConceptsWeighted(String language, Domain domain, String word) {
        return conceptSetToMap(getConcepts(language, domain, word));
    }

    @Override
    public Set<Concept> getConstrainedConcepts(String language, Domain domain, String word, Concept hypernymConcept) {
        checkConcept(hypernymConcept);
        Set<Concept> concepts = getConcepts(language, domain, word);
        Set<Concept> outputConcepts = new HashSet<>();
        for (Concept c : concepts) {
            String cid = c.getID();
            if (cid.equals(hypernymConcept.getID())) {
                outputConcepts.add(c);
            }
            else if (dic.isConnected(cid, hypernymConcept.getID(), -1,
                ERelNameSemantics.HYPERNYM, ERelNameSemantics.HYPERNYMINSTANCE)) {
                outputConcepts.add(c);
            }
        }
        return outputConcepts;
    }

    @Override
    public Map<Concept, Double> getConstrainedConceptsWeighted(String language, Domain domain, String word, Concept hypernymConcept) {
        return conceptSetToMap(getConstrainedConcepts(language, domain, word, hypernymConcept));
    }

    private Map<Concept, Double> conceptSetToMap(Set<Concept> conceptSet) {
        Map<Concept, Double> conceptMap = new HashMap<>();
        for (Concept c : conceptSet) {
            conceptMap.put(c, 1.0);
        }
        return conceptMap;
    }

    /* TODO: implement frequency-based weighting */
    @Override
    public Map<String, Double> getWordsWeighted(String language, Concept concept) {
        checkConcept(concept);
        Map<String, Double> words = new HashMap<>();
        Synset synset = (Synset) concept.getContainer();
        Double sumIndices = 0.0;
        for (Sense sense : synset.getSenses()) {
            sumIndices += sense.getIndex() + 1; // because lowest index = 0
        }
        Double reverseSumIndices = 0.0;
        for (Sense sense : synset.getSenses()) {
            reverseSumIndices += sumIndices - (sense.getIndex() + 1); // because lowest index = 0
        }
        for (Sense sense : synset.getSenses()) {
            Double index = (double) sense.getIndex() + 1;
            Double weight;
            if (reverseSumIndices.compareTo(0.0) == 0) {
                weight = 1.0;
            } else {
                weight = (sumIndices - index) / reverseSumIndices;
            }
            String lemma = sense.getLexicalEntry().getLemmaForm();
            words.put(lemma, weight);
        }
        return words;
    }

    @Override
    public Set<String> getWords(String language, Concept concept) {
        checkConcept(concept);
        Map<String, Double> wordMap = getWordsWeighted(language, concept);
        return wordMap.keySet();
    }

    @Override
    public String getGloss(String language, Concept concept) {
        checkConcept(concept);
        Synset synset = (Synset) concept.getContainer();
        return synset.getGloss();
    }

    @Override
    public Set<Concept> getRelatedConcepts(Concept concept, Set<ConceptRelation> relations) {
        checkConcept(concept);
        Set<Concept> relatedConcepts = new HashSet<>();
        Set<Synset> set = dic.getConnectedSynsets(
                concept.getID(), -1, convertConceptRelations(relations));
        for (Synset relatedSynset : set) {
            relatedConcepts.add(new Concept(relatedSynset, relatedSynset.getId()));
        }
        return relatedConcepts;
    }

    private static Set<String> convertConceptRelations(Set<ConceptRelation> rels) {
        Set<String> relNames = new HashSet<>();
        for (ConceptRelation rel : rels) {
            String[] names = convertRelationName(rel);
            relNames.addAll(Arrays.asList(names));
        }
        return relNames;
    }
    
    private static String[] convertRelationName(ConceptRelation relName) {
        if (relName.equals(IDivAPI.CONCEPT_HYPERNYMY)) {
            return new String[] { ERelNameSemantics.HYPERNYM, ERelNameSemantics.HYPERNYMINSTANCE };
        }
        if (relName.equals(IDivAPI.CONCEPT_HYPONYMY)) {
            return new String[] { ERelNameSemantics.HYPONYM, ERelNameSemantics.HYPONYMINSTANCE };
        }
        if (relName.equals(IDivAPI.CONCEPT_MERONYMY)) {
            return new String[] { ERelNameSemantics.MERONYM };
        }
        if (relName.equals(IDivAPI.CONCEPT_HOLONYMY)) {
            return new String[] { ERelNameSemantics.HOLONYM };
        }
        if (relName.equals(IDivAPI.CONCEPT_SIMILARITY)) {
            return new String[] { ERelNameSemantics.SYNONYMNEAR };
        }
        return new String[] { ERelNameSemantics.RELATED, ERelNameSemantics.SEEALSO,
            ERelNameSemantics.ENTAILS, ERelNameSemantics.ENTAILEDBY,
            ERelNameSemantics.CAUSES, ERelNameSemantics.CAUSEDBY };
    }
    
    private static ConceptRelation convertRelationName(String relName) {
        if (relName.equals(ERelNameSemantics.SYNONYM)) {
            return IDivAPI.CONCEPT_EQUIVALENCE;
        }
        if (relName.equals(ERelNameSemantics.HYPERNYM) ||
            relName.equals(ERelNameSemantics.HYPERNYMINSTANCE)) {
            return IDivAPI.CONCEPT_HYPERNYMY;
        }
        if (relName.equals(ERelNameSemantics.HYPONYM) ||
            relName.equals(ERelNameSemantics.HYPONYMINSTANCE)) {
            return IDivAPI.CONCEPT_HYPONYMY;
        }
        if (relName.equals(ERelNameSemantics.MERONYM)) {
            return IDivAPI.CONCEPT_MERONYMY;
        }
        if (relName.equals(ERelNameSemantics.HOLONYM)) {
            return IDivAPI.CONCEPT_HOLONYMY;
        }
        if (relName.equals(ERelNameSemantics.SYNONYMNEAR)) {
            return IDivAPI.CONCEPT_SIMILARITY;
        }
        return IDivAPI.CONCEPT_RELATEDNESS;
    }

    @Override
    public Map<Concept, Double> getRelatedConceptsWeighted(Concept concept, Set<ConceptRelation> relations) {
        Set<Concept> conceptSet = getRelatedConcepts(concept, relations);
        Map<Concept, Double> conceptMap = new HashMap<>();
        for (Concept c : conceptSet) {
            conceptMap.put(c, 1.0);
        }
        return conceptMap;
    }

    @Override
    public Set<ConceptRelation> getRelations(Concept c1, Concept c2) {
        checkConcept(c1);
        checkConcept(c2);
        Set<String> relNames = dic.getRelations(c1.getID(), c2.getID(), -1);
        Set<ConceptRelation> conceptRelations = new HashSet<>();
        for (String relName : relNames) {
            conceptRelations.add(convertRelationName(relName));
        }
        return conceptRelations;
    }

    @Override
    public Map<ConceptRelation, Double> getRelationsWeighted(Concept c1, Concept c2) {
        Set<ConceptRelation> conceptRelationSet = getRelations(c1, c2);
        Map<ConceptRelation, Double> map = new HashMap<>();
        for (ConceptRelation cr : conceptRelationSet) {
            map.put(cr, 1.0);
        }
        return map;
    }

    @Override
    public Set<String> getLanguages(Concept concept) {
        checkConcept(concept);
        Set<String> langs = new HashSet<>();
        Synset synset = (Synset) concept.getContainer();
        langs.add(synset.getLexicon().getLanguageIdentifier());
        // TODO: add languages of concepts through sense axes
        return langs;
    }

    @Override
    public Set<Domain> getDomains(Concept concept) {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

    @Override
    public Map<Domain, Double> getDomainsWeighted(Concept concept) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Set<String> getLanguages() {
        Set<String> langList = new HashSet<>();
        List<Lexicon> lexicons = dic.getLexicons();
        for (Lexicon l : lexicons) {
            langList.add(l.getLanguageIdentifier());
        }
        return langList;
    }

    /**
     * The current implementation of getDomains is limited as it requires
     * synset-based domain names to be identical to the domain names defined
     * in the DivAPI. The harmonisation of the domain names between the two
     * resources is yet to be done.
     */
    @Override
    public Set<Domain> getDomains() {
        Set<Domain> domainList = new HashSet<>();
        List<Synset> domains = dic.getDomains(null);
        for (Synset domainSynset : domains) {
            for (Sense sense : domainSynset.getSenses()) {
                String lemma = sense.getLexicalEntry().getLemmaForm();
                Domain domain = Domain.getDomain(lemma);
                if (domain != null) {
                    domainList.add(domain);
                } else {
                    System.out.println("Could not find domain " + lemma);
                }
            }
        }
        return domainList;
    }
    
    private static void checkConcept(Concept source) throws NullPointerException {

        if (source == null) {
            throw new IllegalArgumentException("ERROR: received null source!");
        }

        if (source.getID() == null) {
            throw new IllegalArgumentException("ERROR: received null source id!");
        }

        if (source.getID().isEmpty()) {
            throw new IllegalArgumentException("ERROR: received empty source id!");
        }
    }

}
