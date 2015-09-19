package sb2tests;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import beast.core.State;
import beast.core.parameter.RealParameter;
import beast.evolution.alignment.Taxon;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.tree.Node;
import beast.util.TreeParser;
import starbeast2.ConstantPopulation;
import starbeast2.CoordinatedExchange;
import starbeast2.GeneTreeWithinSpeciesTree;
import starbeast2.MultispeciesCoalescent;
import starbeast2.MultispeciesPopulationModel;

public class BigCoordinatedExchangeTest {
    private final String newickSpeciesTree = "((((s1:0.33109175037666511,s3:0.33109175037666511):0.19728320951827943,(s4:0.35745288551058663,s5:0.35745288551058663):0.17092207438435791):0.13952939110386009,(s0:0.23635691859481153,s2:0.23635691859481153):0.43154743240399307):0.24425886940385155,s6:0.91216322040265618)";
    private final String newickGeneTreeA = "((((((s0_tip0:0.347407793400568,s0_tip1:0.347407793400568):0.3249109011518702,s1_tip1:0.6723186945524382):0.016870213500392195,s1_tip2:0.6891889080528304):0.019839817741234667,(((s2_tip0:0.15046603845862686,s2_tip2:0.15046603845862686):0.09011985750867982,s0_tip2:0.24058589596730667):0.26040023056469325,s2_tip1:0.5009861265319999):0.20804259926206514):0.10308010748979513,((((s4_tip1:0.24286247683385803,s4_tip2:0.24286247683385803):0.0774217631281886,s4_tip0:0.32028423996204664):0.3802893107428073,s3_tip0:0.7005735507048539):0.026014759502167695,((s5_tip0:0.31157453809588487,s5_tip2:0.31157453809588487):0.36446702876275516,s5_tip1:0.67604156685864):0.050546743348381606):0.08552052307683855):1.998402609908173,(((s1_tip0:0.33277869115410225,s3_tip2:0.33277869115410225):0.3046584920967114,s3_tip1:0.6374371832508137):0.7842206490201684,((s6_tip1:0.031029864092966437,s6_tip2:0.031029864092966437):0.190295736989861,s6_tip0:0.22132560108282742):1.2003322311881546):1.3888536109210512)";
    private final String newickGeneTreeB = "((((((((s4_tip0:0.11574942025779661,s4_tip2:0.11574942025779661):0.13340648460476376,s4_tip1:0.24915590486256037):0.4203387072191621,s0_tip0:0.6694946120817225):0.0016639946047994902,s2_tip1:0.6711586066865219):0.004588498334115565,s3_tip2:0.6757471050206375):0.08816455259319111,(((s5_tip0:0.15177697100996357,s5_tip2:0.15177697100996357):0.04136320593387219,s5_tip1:0.19314017694383576):0.4459847564129401,s1_tip0:0.6391249333567759):0.12478672425705273):0.8382962467124154,((s6_tip0:0.07184180885800906,s6_tip1:0.07184180885800906):0.02011869319832174,s6_tip2:0.0919605020563308):1.5102474022699133):1.0266078730012804,((((s0_tip1:0.24303930678736413,s0_tip2:0.24303930678736413):0.24352270440468898,(s2_tip0:0.42115724212102956,s2_tip2:0.42115724212102956):0.06540476907102355):0.25003033795794816,((s1_tip2:0.6543745786115609,s3_tip0:0.6543745786115609):0.01621620123129608,s3_tip1:0.670590779842857):0.06600156930714429):0.001545938344288289,s1_tip1:0.7381382874942896):1.890677489833235)";

    final int nSpecies = 7;
    final int individualsPerSpecies = 3;
    final int nBranches = (nSpecies * 2) - 1;
    final double[] popSizes = new double[nBranches];

    GeneTreeWithinSpeciesTree gtwstA;
    GeneTreeWithinSpeciesTree gtwstB;
    TaxonSet speciesSuperSet;
    final double allowedError = 10e-6;

    final TreeParser speciesTree = new TreeParser();
    final TreeParser geneTreeA = new TreeParser();
    final TreeParser geneTreeB = new TreeParser();
    
    final List<GeneTreeWithinSpeciesTree> geneTreeList = new ArrayList<GeneTreeWithinSpeciesTree>();
    
    MultispeciesPopulationModel populationModel;
    MultispeciesCoalescent msc;

    final double ploidy = 2.0;

    final double popSize = 0.3;
    final double expectedLogHR = Math.log(3.0) - Math.log(2.0);
    RealParameter popSizesParameter;

    private static List<Taxon> superSetList(int nSpecies, int individualsPerSpecies) throws Exception {
        List<Taxon> superSetList = new ArrayList<>();
        for (int i = 0; i < nSpecies; i++) {
            final String speciesName = String.format("s%d", i);
            List<Taxon> taxonList = new ArrayList<>();
            for (int j = 0; j < individualsPerSpecies; j++) {
                final String taxonName = String.format("s%d_tip%d", i, j);
                taxonList.add(new Taxon(taxonName));
            }
            superSetList.add(new TaxonSet(speciesName, taxonList));
        }

        return superSetList;
    }

    private void initializeTrees() throws Exception {
        speciesTree.initByName("newick", newickSpeciesTree, "IsLabelledNewick", true);
        geneTreeA.initByName("newick", newickGeneTreeA, "IsLabelledNewick", true);
        geneTreeB.initByName("newick", newickGeneTreeB, "IsLabelledNewick", true);

        speciesSuperSet = new TaxonSet(superSetList(nSpecies, individualsPerSpecies));

        gtwstA = new GeneTreeWithinSpeciesTree();
        gtwstB = new GeneTreeWithinSpeciesTree();

        gtwstA.initByName("tree", geneTreeA, "ploidy", ploidy);
        gtwstB.initByName("tree", geneTreeB, "ploidy", ploidy);

        geneTreeList.add(gtwstA);
        geneTreeList.add(gtwstB);
    }

    @Test
    public void testLogP() throws Exception {
        initializeTrees();

        popSizesParameter = new RealParameter();
        popSizesParameter.initByName("value", String.valueOf(popSize));

        // Create dummy state to allow statenode editing
        State state = new State();
        state.initByName("stateNode", popSizesParameter);
        state.initialise();

        populationModel = new ConstantPopulation();
        populationModel.initByName("popSizes", popSizesParameter);

        msc = new MultispeciesCoalescent();
        msc.initByName("tree", speciesTree, "geneTree", geneTreeList, "taxonSuperSet", speciesSuperSet, "populationModel", populationModel);

        populationModel.initPopSizes(nBranches);
        populationModel.initPopSizes(popSize);

        Node brother = null;
        for (Node n: speciesTree.getRoot().getAllLeafNodes()) {
            if (n.getID().equals("s5")) {
                brother = n.getParent();
            }
        }

        CoordinatedExchange coex = new CoordinatedExchange();
        coex.initByName("multispeciesCoalescent", msc);
        coex.manipulateSpeciesTree(brother);
        final double calculatedLogHR = coex.rearrangeGeneTrees(msc);

        assertEquals(expectedLogHR, calculatedLogHR, allowedError);
    }
}
