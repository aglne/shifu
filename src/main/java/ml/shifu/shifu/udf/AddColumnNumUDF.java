/**
 * Copyright [2012-2014] eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ml.shifu.shifu.udf;

import ml.shifu.shifu.container.obj.ColumnConfig;
import ml.shifu.shifu.exception.ShifuErrorCode;
import ml.shifu.shifu.exception.ShifuException;
import org.apache.commons.lang.StringUtils;
import org.apache.pig.Accumulator;
import org.apache.pig.data.BagFactory;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;
import org.apache.pig.impl.logicalLayer.schema.Schema;

import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * <pre>
 * AddColumnNumUDF class is to convert tuple of row data into bag of column data
 * Its structure is like
 *    {
 * 		(column-id, column-value, column-tag, column-score)
 * 		(column-id, column-value, column-tag, column-score)
 * 		...
 *  }
 */
public class AddColumnNumUDF extends AbstractTrainerUDF<DataBag> implements Accumulator<DataBag>{

    private int weightedColumnNum = -1;

    public AddColumnNumUDF(String source, String pathModelConfig, String pathColumnConfig) throws Exception {
        super(source, pathModelConfig, pathColumnConfig);

        if (!StringUtils.isEmpty(this.modelConfig.getDataSet().getWeightColumnName())) {
            String weightColumnName = this.modelConfig.getDataSet().getWeightColumnName();

            for (int i = 0; i < this.columnConfigList.size(); i++) {
                ColumnConfig config = this.columnConfigList.get(i);
                if (config.getColumnName().equals(weightColumnName)) {
                    this.weightedColumnNum = i;
                    break;
                }
            }
        }

    }

    private DataBag bag;
    private static final TupleFactory tupleFactory = TupleFactory.getInstance();

    public void accumulate(Tuple b) throws IOException {
        if (b == null) return;

        int columnSize = b.size();

        if(columnSize == 0 || columnSize != columnConfigList.size()) {
            throw new RuntimeException("the input records length is not equal to the column config file");
        }

        if(b.get(tagColumnNum) == null) {
            log.warn("the target column is null");
            return;
        }

        bag = BagFactory.getInstance().newDefaultBag();

        String tag = b.get(tagColumnNum).toString();

        for(int i = 0; i < columnSize; i ++){

            if (modelConfig.isCategoricalDisabled()) {
                try {
                    Double.valueOf(b.get(i).toString());
                } catch (Exception e) {
                    continue;
                }
            }

            ColumnConfig config = columnConfigList.get(i);

            if (config.isCandidate()) {

                Tuple tuple = tupleFactory.newTuple(4);

                tuple.set(0, i);
                tuple.set(1, b.get(i));
                tuple.set(2, tag);

                if (weightedColumnNum != -1) {
                    try {
                        tuple.set(3, Double.valueOf(b.get(weightedColumnNum).toString()));
                    } catch (NumberFormatException e) {
                        tuple.set(3, 1.0);
                    }

                    if (i == weightedColumnNum) {
                        //weight and its column, set to 1
                        tuple.set(3, 1.0);
                    }
                } else {
                    tuple.set(3, 1.0);
                }

                bag.add(tuple);
            }
        }
    }

    public void cleanup() {
        if (bag != null) {
            bag.clear();
        }
        bag = BagFactory.getInstance().newDefaultBag();
    }

    public DataBag getValue() {
        return bag;
    }

    @Override
    public DataBag exec(Tuple input) throws IOException {

        cleanup();

        accumulate(input);

        return getValue();
    }

    @Override
    public Schema outputSchema(Schema input) {
        return null;
    }

}