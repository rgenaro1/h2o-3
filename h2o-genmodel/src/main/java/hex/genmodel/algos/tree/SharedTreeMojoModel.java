package hex.genmodel.algos.tree;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.drf.DrfMojoModel;
import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.utils.ByteBufferWrapper;
import hex.genmodel.utils.GenmodelBitSet;

import java.util.Arrays;

/**
 * Common ancestor for {@link DrfMojoModel} and {@link GbmMojoModel}.
 * See also: `hex.tree.SharedTreeModel` and `hex.tree.TreeVisitor` classes.
 */
public abstract class SharedTreeMojoModel extends MojoModel {
    private static final int NsdNaVsRest = NaSplitDir.NAvsREST.value();
    private static final int NsdNaLeft = NaSplitDir.NALeft.value();
    private static final int NsdLeft = NaSplitDir.Left.value();

    /**
     * {@code _ntree_groups} is the number of trees requested by the user. For
     * binomial case or regression this is also the total number of trees
     * trained; however in multinomial case each requested "tree" is actually
     * represented as a group of trees, with {@code _ntrees_per_group} trees
     * in each group. Each of these individual trees assesses the likelihood
     * that a given observation belongs to class A, B, C, etc. of a
     * multiclass response.
     */
    protected int _ntree_groups;
    protected int _ntrees_per_group;
    /**
     * Array of binary tree data, each tree being a {@code byte[]} array. The
     * trees are logically grouped into a rectangular grid of dimensions
     * {@link #_ntree_groups} x {@link #_ntrees_per_group}, however physically
     * they are stored as 1-dimensional list, and an {@code [i, j]} logical
     * tree is mapped to the index {@link #treeIndex(int, int)}.
     */
    protected byte[][] _compressed_trees;


    /**
     * Highly efficient (critical path) tree scoring
     *
     * Given a tree (in the form of a byte array) and the row of input data, compute either this tree's
     * predicted value when `computeLeafAssignment` is false, or the the decision path within the tree (but no more
     * than 64 levels) when `computeLeafAssignment` is true.
     *
     * Note: this function is also used from the `hex.tree.CompressedTree` class in `h2o-algos` project.
     */
    @SuppressWarnings("ConstantConditions")  // Complains that the code is too complex. Well duh!
    public static double scoreTree(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment) {
        ByteBufferWrapper ab = new ByteBufferWrapper(tree);
        GenmodelBitSet bs = null;  // Lazily set on hitting first group test
        long bitsRight = 0;
        int level = 0;
        while (true) {
            int nodeType = ab.get1U();
            int colId = ab.get2();
            if (colId == 65535) return ab.get4f();
            int naSplitDir = ab.get1U();
            boolean naVsRest = naSplitDir == NsdNaVsRest;
            boolean leftward = naSplitDir == NsdNaLeft || naSplitDir == NsdLeft;
            int lmask = (nodeType & 51);
            int equal = (nodeType & 12);  // Can be one of 0, 8, 12
            assert equal != 4;  // no longer supported

            float splitVal = -1;
            if (!naVsRest) {
                // Extract value or group to split on
                if (equal == 0) {
                    // Standard float-compare test (either < or ==)
                    splitVal = ab.get4f();  // Get the float to compare
                } else {
                    // Bitset test
                    if (bs == null) bs = new GenmodelBitSet(0);
                    if (equal == 8)
                        bs.fill2(tree, ab);
                    else
                        bs.fill3(tree, ab);
                }
            }
            float weightL = ab.get4f();
            float weightR = ab.get4f();

            double d = row[colId];
            if (Double.isNaN(d)? !leftward : !naVsRest && (equal == 0? d >= splitVal : bs.contains((int)d))) {
                // go RIGHT
                switch (lmask) {
                    case 0:  ab.skip(ab.get1U());  break;
                    case 1:  ab.skip(ab.get2());  break;
                    case 2:  ab.skip(ab.get3());  break;
                    case 3:  ab.skip(ab.get4());  break;
                    case 16: ab.skip(nclasses < 256? 1 : 2);  break;  // Small leaf
                    case 48: ab.skip(4);  break;  // skip the prediction
                    default:
                        assert false : "illegal lmask value " + lmask + " in tree " + Arrays.toString(tree);
                }
                if (computeLeafAssignment && level < 64) bitsRight |= 1 << level;
                lmask = (nodeType & 0xC0) >> 2;  // Replace leftmask with the rightmask
            } else {
                // go LEFT
                if (lmask <= 3)
                    ab.skip(lmask + 1);
            }

            level++;
            if ((lmask & 16) != 0) {
                if (computeLeafAssignment) {
                    bitsRight |= 1 << level;  // mark the end of the tree
                    return Double.longBitsToDouble(bitsRight);
                } else {
                    return ab.get4f();
                }
            }
        }
    }

    public static double scoreTree(byte[] tree, double[] row, int nclasses) {
        return scoreTree(tree, row, nclasses, false);
    }

    public static String getDecisionPath(double leafAssignment) {
        long l = Double.doubleToRawLongBits(leafAssignment);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < 64; ++i) {
            boolean right = ((l>>i) & 0x1L) == 1;
            sb.append(right? "R" : "L");
            if (right) pos = i;
        }
        return sb.substring(0, pos);
    }

    //------------------------------------------------------------------------------------------------------------------
    // Computing a Tree Graph
    //------------------------------------------------------------------------------------------------------------------

    private void computeTreeGraph(SharedTreeSubgraph sg, SharedTreeNode node, byte[] tree, ByteBufferWrapper ab, int nclasses) {
        int nodeType = ab.get1U();
        int colId = ab.get2();
        if (colId == 65535) {
            float leafValue = ab.get4f();
            node.setLeafValue(leafValue);
            return;
        }
        String colName = getNames()[colId];
        node.setCol(colId, colName);

        int naSplitDir = ab.get1U();
        boolean naVsRest = naSplitDir == NsdNaVsRest;
        boolean leftward = naSplitDir == NsdNaLeft || naSplitDir == NsdLeft;
        node.setLeftward(leftward);
        node.setNaVsRest(naVsRest);

        int lmask = (nodeType & 51);
        int equal = (nodeType & 12);  // Can be one of 0, 8, 12
        assert equal != 4;  // no longer supported

        if (!naVsRest) {
            // Extract value or group to split on
            if (equal == 0) {
                // Standard float-compare test (either < or ==)
                float splitVal = ab.get4f();  // Get the float to compare
                node.setSplitValue(splitVal);
            } else {
                // Bitset test
                GenmodelBitSet bs = new GenmodelBitSet(0);
                if (equal == 8)
                    bs.fill2(tree, ab);
                else
                    bs.fill3(tree, ab);
                node.setBitset(getDomainValues(colId), bs);
            }
        }

        // This logic:
        //
        //        double d = row[colId];
        //        if (Double.isNaN(d)? !leftward : !naVsRest && (equal == 0? d >= splitVal : bs.contains((int)d))) {

        // Really does this:
        //
        //        if (value is NaN) {
        //            if (leftward) {
        //                go left
        //            }
        //            else {
        //                go right
        //            }
        //        }
        //        else {
        //            if (naVsRest) {
        //                go left
        //            }
        //            else {
        //                if (numeric) {
        //                    if (value < split value) {
        //                        go left
        //                    }
        //                    else {
        //                        go right
        //                    }
        //                }
        //                else {
        //                    if (value not in bitset) {
        //                        go left
        //                    }
        //                    else {
        //                        go right
        //                    }
        //                }
        //            }
        //        }

        float weightL = ab.get4f();
        float weightR = ab.get4f();
        node.setWeight(weightL,weightR);

        // go RIGHT
        {
            ByteBufferWrapper ab2 = new ByteBufferWrapper(tree);
            ab2.skip(ab.position());

            switch (lmask) {
                case 0:
                    ab2.skip(ab2.get1U());
                    break;
                case 1:
                    ab2.skip(ab2.get2());
                    break;
                case 2:
                    ab2.skip(ab2.get3());
                    break;
                case 3:
                    ab2.skip(ab2.get4());
                    break;
                case 16:
                    ab2.skip(nclasses < 256 ? 1 : 2);
                    break;  // Small leaf
                case 48:
                    ab2.skip(4);
                    break;  // skip the prediction
                default:
                    assert false : "illegal lmask value " + lmask + " in tree " + Arrays.toString(tree);
            }
            int lmask2 = (nodeType & 0xC0) >> 2;  // Replace leftmask with the rightmask

            SharedTreeNode newNode = sg.makeRightChildNode(node);
            if ((lmask2 & 16) != 0) {
                float leafValue = ab2.get4f();
                newNode.setLeafValue(leafValue);
            }
            else {
                computeTreeGraph(sg, newNode, tree, ab2, nclasses);
            }
        }

        // go LEFT
        {
            ByteBufferWrapper ab2 = new ByteBufferWrapper(tree);
            ab2.skip(ab.position());

            if (lmask <= 3)
                ab2.skip(lmask + 1);

            SharedTreeNode newNode = sg.makeLeftChildNode(node);
            if ((lmask & 16) != 0) {
                float leafValue = ab2.get4f();
                newNode.setLeafValue(leafValue);
            }
            else {
                computeTreeGraph(sg, newNode, tree, ab2, nclasses);
            }
        }
    }

    /**
     * Compute a graph of the forest.
     *
     * @return A graph of the forest.
     */
    public SharedTreeGraph _computeGraph(int treeToPrint) {
        SharedTreeGraph g = new SharedTreeGraph();

        if (treeToPrint >= _ntree_groups) {
            throw new IllegalArgumentException("Tree " + treeToPrint + " does not exist (max " + _ntree_groups + ")");
        }

        int j;
        if (treeToPrint >= 0) {
            j = treeToPrint;
        }
        else {
            j = 0;
        }

        for (; j < _ntree_groups; j++) {
            for (int i = 0; i < _ntrees_per_group; i++) {
                String className = "";
                {
                    String[] domainValues = getDomainValues(getResponseIdx());
                    if (domainValues != null) {
                        className = ", Class " + domainValues[i];
                    }
                }
                int itree = treeIndex(j, i);

                SharedTreeSubgraph sg = g.makeSubgraph("Tree " + j + className);
                SharedTreeNode node = sg.makeRootNode();
                byte[] tree = _compressed_trees[itree];
                ByteBufferWrapper ab = new ByteBufferWrapper(tree);
                computeTreeGraph(sg, node, tree, ab, _nclasses);
            }

            if (treeToPrint >= 0) {
                break;
            }
        }

        return g;
    }

    //------------------------------------------------------------------------------------------------------------------
    // Private
    //------------------------------------------------------------------------------------------------------------------

    protected SharedTreeMojoModel(String[] columns, String[][] domains) {
        super(columns, domains);
    }

    /**
     * Score all trees and fill in the `preds` array.
     */
    protected void scoreAllTrees(double[] row, double[] preds) {
        java.util.Arrays.fill(preds, 0);
        for (int i = 0; i < _ntrees_per_group; i++) {
            int k = _nclasses == 1? 0 : i + 1;
            for (int j = 0; j < _ntree_groups; j++) {
                int itree = treeIndex(j, i);
                preds[k] += scoreTree(_compressed_trees[itree], row, _nclasses);
            }
        }
    }

    protected int treeIndex(int groupIndex, int classIndex) {
        return classIndex * _ntree_groups + groupIndex;
    }

}
