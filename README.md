# TreeUtils
A simple Scala tool to handle running the PHILIP MIX parsimony program and generating JSON output trees. You'll need a compiled executable of [PHYLIP](https://evolution.genetics.washington.edu/phylip.html) available on your system.

## Building

TreeUtils can be built with Scala and [sbt](https://www.scala-sbt.org/). To build the executable you'll need to run:

```
sbt assembly
```

This should make an executable jar file in the subdirectory:

```
./target/scala-2.12/TreeUtils-assembly-1.4.jar
```

## Tree building script:

```

    java -jar -Xmx8g TreeUtils-assembly-1.4.jar \
         -allelesFile=<your allReadCounts> \
         -mixEXEC=<where to find mix> \
         -mixRunLocation=<what directory do we want to run mix in> \
         -outputTable=<output file of alleles>.txt \
         -outputTree=<tree output in json>.json \
         --annotations=<any annotations you'd like added to the tree> \
         -sample=<your sample name>
```
You need to fill in the parameters above. The allReadCounts file should look like the following:

```
events  index   total_counts    total_proportion
NONE_NONE_NONE_313D+196_313D+196_313D+196_313D+196_313D+196_313D+196_313D+196   1       45019   0.272177650949499
NONE_NONE_NONE_NONE_108D+221_108D+221_108D+221_108D+221_108D+221_NONE   2       58925   0.356251095808419
NONE_216D+124_216D+124_216D+124_216D+124_216D+124_216D+124_216D+124_216D+124_NONE       3       10162   0.0614378215630914
NONE_NONE_NONE_NONE_NONE_3D+250_54D+275_54D+275_54D+275_NONE    4       21900   0.132403886265666
NONE_NONE_NONE_NONE_53D+222_53D+222_53D+222_NONE_5D+326_NONE    5       13159   0.0795572027109545
```

Where each column, seperated by tabs, describes the editing outcome (separated by _'s), the index, the total count, and the proportion of cells/reads in which this editing pattern is seen. If you want to add annotations to the trees you'll need the annotation file above. This file has one cell per line, like so:

```
cellID	hmid	detectable	annotation1	annotation2	annotation3
1	NONE_NONE_NONE_313D+196_313D+196_313D+196_313D+196_313D+196_313D+196_313D+196	0	6182	0	7610
2	NONE_NONE_NONE_NONE_108D+221_108D+221_108D+221_108D+221_108D+221_NONE	1	801	30521	234
3	NONE_216D+124_216D+124_216D+124_216D+124_216D+124_216D+124_216D+124_216D+124_NONE	1	556	2370	855
4	NONE_NONE_NONE_NONE_NONE_3D+250_54D+275_54D+275_54D+275_NONE	1	249	10740	118
5	NONE_NONE_NONE_NONE_53D+222_53D+222_53D+222_NONE_5D+326_NONE	1	165	7503	207
6	NONE_381D+142_381D+142_381D+142_381D+142_381D+142_381D+142_381D+142_381D+142_381D+142	0	49	0	53
7	NONE_NONE_NONE_313D+197_313D+197_313D+197_313D+197_313D+197_313D+197_313D+197	0	37	0	24
8	NONE_NONE_NONE_1D+195_312D+201_312D+201_312D+201_312D+201_312D+201_312D+201	0	26	0	34![image](https://user-images.githubusercontent.com/1264127/218544126-f3aef5b7-f93a-42f4-b728-ef8cc55b6222.png)
```

The first two columns are required, the first being a linear list of cell IDs, the second being a editing outcome for the cell, which is used to match against the tree. The follow columns name are arbitrary and you should name them as you'd like to see on the resulting tree. For instance annotation1 could be changed to reads_captured_in_this_cell and you could fill the column with the number of RNA seq reads you saw for this sample. 


