package hex.genmodel.algos.tree;

import hex.genmodel.utils.GenmodelBitSet;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;

/**
 * Node in a tree.
 * A node (optionally) contains left and right edges to the left and right child nodes.
 */
class SharedTreeNode {
  private final SharedTreeNode parent;
  private final int subgraphNumber;
  private final int nodeNumber;
  private float weightL;
  private float weightR;
  private final int depth;
  private int colId;
  private String colName;
  private boolean leftward;
  private boolean naVsRest;
  private float splitValue = Float.NaN;
  private String[] domainValues;
  private GenmodelBitSet bs;
  private float leafValue = Float.NaN;
  private SharedTreeNode leftChild;
  private SharedTreeNode rightChild;

  // Whether NA for this colId is reachable to this node.
  private boolean inclusiveNa;

  // When a column is categorical, levels that are reachable to this node.
  // This in particular includes any earlier splits of the same colId.
  private BitSet inclusiveLevels;

  /**
   * Create a new node.
   * @param p Parent node
   * @param sn Tree number
   * @param n Node number
   * @param d Node depth within the tree
   */
  SharedTreeNode(SharedTreeNode p, int sn, int n, int d, float wL, float wR) {
    parent = p;
    subgraphNumber = sn;
    nodeNumber = n;
    depth = d;
    weightL = wL;
    weightR = wR;
    System.out.println("Initialized weights to " + wL + " " + wR);
  }

  public int getDepth() {
    return depth;
  }

  private int getNodeNumber() {
    return nodeNumber;
  }

  float getNodeWeightL() {
    return weightL;
  }

  float getNodeWeightR() {
    return weightR;
  }

  void setWeight(float wL, float wR) {
    weightL = wL;
    weightR = wR;
    System.out.println("Setting weights to " + wL + " " + wR);
  }

  void setCol(int v1, String v2) {
    colId = v1;
    colName = v2;
  }

  private int getColId() {
    return colId;
  }

  void setLeftward(boolean v) {
    leftward = v;
  }

  void setNaVsRest(boolean v) {
    naVsRest = v;
  }

  void setSplitValue(float v) {
    splitValue = v;
  }

  void setBitset(String[] v1, GenmodelBitSet v2) {
    assert (v1 != null);
    domainValues = v1;
    bs = v2;
  }

  void setLeafValue(float v) {
    leafValue = v;
  }

  /**
   * Calculate whether the NA value for a particular colId can reach this node.
   * @param colIdToFind Column id to find
   * @return true if NA of colId reaches this node, false otherwise
   */
  private boolean findInclusiveNa(int colIdToFind) {
    if (parent == null) {
      return true;
    }
    else if (parent.getColId() == colIdToFind) {
      return inclusiveNa;
    }
    return parent.findInclusiveNa(colIdToFind);
  }

  private boolean calculateChildInclusiveNa(boolean includeThisSplitEdge) {
    return findInclusiveNa(colId) && includeThisSplitEdge;
  }

  /**
   * Find the set of levels for a particular categorical column that can reach this node.
   * A null return value implies the full set (i.e. every level).
   * @param colIdToFind Column id to find
   * @return Set of levels
   */
  private BitSet findInclusiveLevels(int colIdToFind) {
    if (parent == null) {
      return null;
    }
    if (parent.getColId() == colIdToFind) {
      return inclusiveLevels;
    }
    return parent.findInclusiveLevels(colIdToFind);
  }

  private boolean calculateIncludeThisLevel(BitSet inheritedInclusiveLevels, int i) {
    if (inheritedInclusiveLevels == null) {
      // If there is no prior split history for this column, then treat the
      // inherited set as complete.
      return true;
    }
    else if (inheritedInclusiveLevels.get(i)) {
      // Allow levels that flowed into this node.
      return true;
    }

    // Filter out levels that were already discarded from a previous split.
    return false;
  }

  /**
   * Calculate the set of levels that flow through to a child.
   * @param includeAllLevels naVsRest dictates include all (inherited) levels
   * @param discardAllLevels naVsRest dictates discard all levels
   * @param nodeBitsetDoesContain true if the GenmodelBitset from the compressed_tree
   * @return Calculated set of levels
   */
  private BitSet calculateChildInclusiveLevels(boolean includeAllLevels,
                                               boolean discardAllLevels,
                                               boolean nodeBitsetDoesContain) {
    BitSet inheritedInclusiveLevels = findInclusiveLevels(colId);
    BitSet childInclusiveLevels = new BitSet();

    for (int i = 0; i < domainValues.length; i++) {
      // Calculate whether this level should flow into this child node.
      boolean includeThisLevel = false;
      {
        if (discardAllLevels) {
          includeThisLevel = false;
        }
        else if (includeAllLevels) {
          includeThisLevel = calculateIncludeThisLevel(inheritedInclusiveLevels, i);
        }
        else if (bs.contains(i) == nodeBitsetDoesContain) {
          includeThisLevel = calculateIncludeThisLevel(inheritedInclusiveLevels, i);
        }
      }

      if (includeThisLevel) {
        childInclusiveLevels.set(i);
      }
    }
    return childInclusiveLevels;
  }

  void setLeftChild(SharedTreeNode v) {
    leftChild = v;

    boolean childInclusiveNa = calculateChildInclusiveNa(leftward);
    v.setInclusiveNa(childInclusiveNa);

    if (! isBitset()) {
      return;
    }
    BitSet childInclusiveLevels = calculateChildInclusiveLevels(naVsRest, false, false);
    v.setInclusiveLevels(childInclusiveLevels);
  }

  void setRightChild(SharedTreeNode v) {
    rightChild = v;

    boolean childInclusiveNa = calculateChildInclusiveNa(!leftward);
    v.setInclusiveNa(childInclusiveNa);

    if (! isBitset()) {
      return;
    }
    BitSet childInclusiveLevels = calculateChildInclusiveLevels(false, naVsRest, true);
    v.setInclusiveLevels(childInclusiveLevels);
  }

  void setInclusiveNa(boolean v) {
    inclusiveNa = v;
  }

  private boolean getInclusiveNa() {
    return inclusiveNa;
  }

  private void setInclusiveLevels(BitSet v) {
    inclusiveLevels = v;
  }

  private BitSet getInclusiveLevels() {
    return inclusiveLevels;
  }

  public String getName() {
    return "Node " + nodeNumber;
  }

  public void print() {
    System.out.println("        Node " + nodeNumber);
    System.out.println("            weight(L/R):       " + weightL + " / " + weightR);
    System.out.println("            depth:       " + depth);
    System.out.println("            colId:       " + colId);
    System.out.println("            colName:     " + ((colName != null) ? colName : ""));
    System.out.println("            leftward:    " + leftward);
    System.out.println("            naVsRest:    " + naVsRest);
    System.out.println("            splitVal:    " + splitValue);
    System.out.println("            isBitset:    " + isBitset());
    System.out.println("            leafValue:   " + leafValue);
    System.out.println("            leftChild:   " + ((leftChild != null) ? leftChild.getName() : ""));
    System.out.println("            rightChild:  " + ((rightChild != null) ? rightChild.getName() : ""));
  }

  void printEdges() {
    if (leftChild != null) {
      System.out.println("        " + getName() + " ---left---> " + leftChild.getName());
      leftChild.printEdges();
    }
    if (rightChild != null) {
      System.out.println("        " + getName() + " ---right--> " + rightChild.getName());
      rightChild.printEdges();
    }
  }

  private String getDotName() {
    return "SG_" + subgraphNumber + "_Node_" + nodeNumber;
  }

  private boolean isLeaf() {
    return (! Float.isNaN(leafValue));
  }

  private boolean isBitset() {
    return (domainValues != null);
  }

  private void printDotNode(PrintStream os, boolean detail) {
    os.print("\"" + getDotName() + "\"");
    os.print(" [");

    if (isLeaf()) {
      os.print("label=\"");
      os.print(leafValue);
    }
    else if (isBitset()) {
      os.print("shape=box,label=\"");
      os.print(colName);
    }
    else {
      assert(! Float.isNaN(splitValue));
      os.print("shape=box,label=\"");
      os.print(colName + " < " + splitValue);
    }

    if (detail) {
      os.print("\\n\\nN" + getNodeNumber());
      if (getNodeWeightL()==0)
        os.print("\\n\\nW: " + getNodeWeightR());
      else if (getNodeWeightR()==0)
        os.print("\\n\\nW: " + getNodeWeightL());
      else
        os.print("\\n\\nW: " + (getNodeWeightL() + getNodeWeightR()));
      if (naVsRest) {
        os.print("\\n" + "nasVsRest");
      }
      if (leftChild != null) {
        os.print("\\n" + "L: N" + leftChild.getNodeNumber());
      }
      if (rightChild != null) {
        os.print("\\n" + "R: N" + rightChild.getNodeNumber());
      }
    }

    os.print("\"]");
    os.println("");
  }

  /**
   * Recursively print nodes at a particular depth level in the tree.  Useful to group them so they render properly.
   * @param os output stream
   * @param levelToPrint level number
   * @param detail include addtional node detail information
   */
  void printDotNodesAtLevel(PrintStream os, int levelToPrint, boolean detail) {
    if (getDepth() == levelToPrint) {
      printDotNode(os, detail);
      return;
    }

    assert (getDepth() < levelToPrint);

    if (leftChild != null) {
      leftChild.printDotNodesAtLevel(os, levelToPrint, detail);
    }
    if (rightChild != null) {
      rightChild.printDotNodesAtLevel(os, levelToPrint, detail);
    }
  }

  private void printDotEdgesCommon(PrintStream os, int maxLevelsToPrintPerEdge, ArrayList<String> arr, SharedTreeNode child) {
    if (isBitset()) {
      BitSet childInclusiveLevels = child.getInclusiveLevels();
      int total = childInclusiveLevels.cardinality();
      if ((total > 0) && (total <= maxLevelsToPrintPerEdge)) {
        for (int i = childInclusiveLevels.nextSetBit(0); i >= 0; i = childInclusiveLevels.nextSetBit(i+1)) {
          arr.add(domainValues[i]);
        }
      }
      else {
        arr.add(total + " levels");
      }
    }
    os.print("label=\"");
    for (String s : arr) {
      os.print(s + "\\n");
    }
    os.print("\"");
    os.println("]");
  }

  /**
   * Recursively print all edges in the tree.
   * @param os output stream
   * @param maxLevelsToPrintPerEdge Limit the number of individual categorical level names printed per edge
   */
  void printDotEdges(PrintStream os, int maxLevelsToPrintPerEdge) {
    assert (leftChild == null) == (rightChild == null);

    if (leftChild != null) {
      os.print("\"" + getDotName() + "\"" + " -> " + "\"" + leftChild.getDotName() + "\"" + " [");

      ArrayList<String> arr = new ArrayList<>();
      if (leftChild.getInclusiveNa()) {
        arr.add("[NA]");
      }

      if (naVsRest) {
        arr.add("[Not NA]");
      }
      else {
        if (! isBitset()) {
          arr.add("<");
        }
      }

      printDotEdgesCommon(os, maxLevelsToPrintPerEdge, arr, leftChild);
    }

    if (rightChild != null) {
      os.print("\"" + getDotName() + "\"" + " -> " + "\"" + rightChild.getDotName() + "\"" + " [");

      ArrayList<String> arr = new ArrayList<>();
      if (rightChild.getInclusiveNa()) {
        arr.add("[NA]");
      }

      if (! naVsRest) {
        if (! isBitset()) {
          arr.add(">=");
        }
      }

      printDotEdgesCommon(os, maxLevelsToPrintPerEdge, arr, rightChild);
    }
  }
}
