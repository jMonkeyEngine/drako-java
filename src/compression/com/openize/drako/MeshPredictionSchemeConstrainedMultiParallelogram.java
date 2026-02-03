package com.openize.drako;
import com.openize.drako.AsposeUtils;
import com.openize.drako.BoolSpan;
import com.openize.drako.HashBuilder;
import com.openize.drako.IntSpan;
import com.openize.drako.Struct;
import java.io.Serializable;
import java.util.ArrayList;
class MeshPredictionSchemeConstrainedMultiParallelogram extends MeshPredictionScheme
{    
    static final class PredictionConfiguration implements Struct<PredictionConfiguration>, Serializable
    {
        public Error error = new Error();
        public byte configuration;
        public int num_used_parallelograms;
        public int[] predicted_value;
        public int[] residuals;
        public PredictionConfiguration()
        {
        }
        
        private PredictionConfiguration(PredictionConfiguration other)
        {
            this.error = Struct.byVal(other.error);
            this.configuration = other.configuration;
            this.num_used_parallelograms = other.num_used_parallelograms;
            this.predicted_value = other.predicted_value;
            this.residuals = other.residuals;
        }
        
        @Override
        public PredictionConfiguration clone()
        {
            return new PredictionConfiguration(this);
        }
        
        @Override
        public void copyFrom(PredictionConfiguration src)
        {
            if (src == null)
                return;
            this.error = Struct.byVal(src.error);
            this.configuration = src.configuration;
            this.num_used_parallelograms = src.num_used_parallelograms;
            this.predicted_value = src.predicted_value;
            this.residuals = src.residuals;
        }
        
        static final long serialVersionUID = -988565132L;
        @Override
        public int hashCode()
        {
            HashBuilder builder = new HashBuilder();
            builder.hash(this.error);
            builder.hash(this.configuration);
            builder.hash(this.num_used_parallelograms);
            builder.hash(this.predicted_value);
            builder.hash(this.residuals);
            return builder.hashCode();
        }
        
        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof PredictionConfiguration))
                return false;
            PredictionConfiguration rhs = (PredictionConfiguration)obj;
            if (!AsposeUtils.equals(this.error, rhs.error))
                return false;
            if (this.configuration != rhs.configuration)
                return false;
            if (this.num_used_parallelograms != rhs.num_used_parallelograms)
                return false;
            if (!AsposeUtils.equals(this.predicted_value, rhs.predicted_value))
                return false;
            if (!AsposeUtils.equals(this.residuals, rhs.residuals))
                return false;
            return true;
        }
        
    }
    
    static final int OPTIMAL_MULTI_PARALLELOGRAM = 0;
    static final int K_MAX_NUM_PARALLELOGRAMS = 4;
    // Crease edges are used to store whether any given edge should be used for
    // parallelogram prediction or not. New values are added in the order in which
    // the edges are processed. For better compression, the flags are stored in
    // in separate contexts based on the number of available parallelograms at a
    // given vertex.
    // 
    ArrayList<Boolean>[] is_crease_edge_;
    public MeshPredictionSchemeConstrainedMultiParallelogram(PointAttribute attribute, PredictionSchemeTransform transform, MeshPredictionSchemeData meshData)
    {
        super(attribute, transform, meshData);
        this.$initFields$();
        for (int i = 0; i < K_MAX_NUM_PARALLELOGRAMS; ++i)
        {
            is_crease_edge_[i] = new ArrayList<Boolean>();
        }
        
    }
    
    public int getPredictionMethod()
    {
        return PredictionSchemeMethod.CONSTRAINED_MULTI_PARALLELOGRAM;
    }
    
    int[] entropy_symbols_;
    ShannonEntropyTracker entropy_tracker_;
    @Override
    public void computeCorrectionValues(IntSpan in_data, IntSpan out_corr, int size, int num_components, int[] entry_to_point_id_map)
    {
        this.transform_.initializeEncoding(in_data, num_components);
        ICornerTable table = this.meshData.getCornerTable();
        int[] vertex_to_data_map = this.meshData.vertexToDataMap;
        int[][] pred_vals = new int[K_MAX_NUM_PARALLELOGRAMS][];
        for (int i = 0; i < K_MAX_NUM_PARALLELOGRAMS; ++i)
        {
            pred_vals[i] = new int[num_components];
        }
        
        IntSpan multi_pred_vals = IntSpan.wrap(new int[num_components]);
        this.entropy_symbols_ = new int[num_components];
        BoolSpan exluded_parallelograms = BoolSpan.wrap(new boolean[K_MAX_NUM_PARALLELOGRAMS]);
        long[] total_used_parallelograms = new long[K_MAX_NUM_PARALLELOGRAMS];
        long[] total_parallelograms = new long[K_MAX_NUM_PARALLELOGRAMS];
        IntSpan current_residuals = IntSpan.wrap(new int[num_components]);
        
        // We start processing the vertices from the end because this prediction uses
        // data from previous entries that could be overwritten when an entry is
        // processed.
        for (int p = this.meshData.dataToCornerMap.getCount() - 1; p > 0; --p)
        {
            int start_corner_id = this.meshData.dataToCornerMap.get(p);
            int corner_id = start_corner_id;
            int num_parallelograms = 0;
            boolean first_pass = true;
            while (corner_id != CornerTable.K_INVALID_CORNER_INDEX)
            {
                if (MeshPredictionSchemeParallelogram.computeParallelogramPrediction(p, corner_id, table, vertex_to_data_map, in_data, num_components, IntSpan.wrap(pred_vals[num_parallelograms])))
                {
                    // Parallelogram prediction applied and stored in
                    // |pred_vals[num_parallelograms]|
                    ++num_parallelograms;
                    // Stop processing when we reach the maximum number of allowed
                    // parallelograms.
                    if (num_parallelograms == K_MAX_NUM_PARALLELOGRAMS)
                        break;
                }
                
                
                // Proceed to the next corner attached to the vertex. First swing left
                // and if we reach a boundary, swing right from the start corner.
                if (first_pass)
                {
                    corner_id = table.swingLeft(corner_id);
                }
                else
                {
                    corner_id = table.swingRight(corner_id);
                }
                
                if (corner_id == start_corner_id)
                    break;
                if (corner_id == CornerTable.K_INVALID_CORNER_INDEX && first_pass)
                {
                    first_pass = false;
                    corner_id = table.swingRight(start_corner_id);
                }
                
            }
            
            int dst_offset = p * num_components;
            Error error = new Error();
            PredictionConfiguration best_prediction = new PredictionConfiguration();
            int src_offset = (p - 1) * num_components;
            error.copyFrom(this.computeError(in_data.slice(src_offset), in_data.slice(dst_offset), current_residuals, num_components));
            
            if (num_parallelograms > 0)
            {
                total_parallelograms[num_parallelograms - 1] += num_parallelograms;
                long new_overhead_bits = this.computeOverheadBits(total_used_parallelograms[num_parallelograms - 1], total_parallelograms[num_parallelograms - 1]);
                error.num_bits += new_overhead_bits;
            }
            
            
            best_prediction.error.copyFrom(error);
            best_prediction.configuration = 0;
            best_prediction.num_used_parallelograms = 0;
            best_prediction.predicted_value = new int[num_components];
            System.arraycopy(in_data.toArray(), src_offset, best_prediction.predicted_value, 0, num_components);
            best_prediction.residuals = new int[num_components];
            System.arraycopy(current_residuals.toArray(), 0, best_prediction.residuals, 0, num_components);
            
            // Compute prediction error for different cases of used parallelograms.
            for (int num_used_parallelograms = 1; num_used_parallelograms <= num_parallelograms; ++num_used_parallelograms)
            {
                // Mark all parallelograms as excluded.
                for (int j = 0; j < num_parallelograms; ++j)
                {
                    exluded_parallelograms.put(j, true);
                }
                
                // Mark the first |num_used_parallelograms| as not excluded.
                for (int j = 0; j < num_used_parallelograms; ++j)
                {
                    exluded_parallelograms.put(j, false);
                }
                
                // Permute over the excluded edges and compute error for each
                // configuration (permutation of excluded parallelograms).
                do
                {
                    // Reset the multi-parallelogram predicted values.
                    for (int j = 0; j < num_components; ++j)
                    {
                        multi_pred_vals.put(j, 0);
                    }
                    
                    int configuration = 0;
                    for (int j = 0; j < num_parallelograms; ++j)
                    {
                        if (exluded_parallelograms.get(j))
                            continue;
                        for (int c = 0; c < num_components; ++c)
                        {
                            multi_pred_vals.put(c, multi_pred_vals.get(c) + pred_vals[j][c]);
                        }
                        
                        // Set jth bit of the configuration.
                        configuration |= 1 << j;
                    }
                    
                    
                    for (int j = 0; j < num_components; ++j)
                    {
                        multi_pred_vals.put(j, multi_pred_vals.get(j) / num_used_parallelograms);
                    }
                    
                    error.copyFrom(this.computeError(multi_pred_vals, in_data.slice(dst_offset), current_residuals, num_components));
                    if (num_parallelograms > 0)
                    {
                        long new_overhead_bits = this.computeOverheadBits(total_used_parallelograms[num_parallelograms - 1] + num_used_parallelograms, total_parallelograms[num_parallelograms - 1]);
                        
                        // Add overhead bits to the total error.
                        error.num_bits += new_overhead_bits;
                    }
                    
                    if (Error.op_lt(error, best_prediction.error))
                    {
                        best_prediction.error.copyFrom(error);
                        best_prediction.configuration = (byte)configuration;
                        best_prediction.num_used_parallelograms = num_used_parallelograms;
                        best_prediction.predicted_value = new int[num_components];
                        for (int j = 0; j < num_components; ++j)
                        {
                            best_prediction.predicted_value[j] = multi_pred_vals.get(j);
                        }
                        
                        best_prediction.residuals = new int[num_components];
                        System.arraycopy(current_residuals.toArray(), 0, best_prediction.residuals, 0, num_components);
                    }
                    
                } while (this.nextPermutation(exluded_parallelograms, num_parallelograms));
                
            }
            
            if (num_parallelograms > 0)
            {
                total_used_parallelograms[num_parallelograms - 1] += best_prediction.num_used_parallelograms;
            }
            
            
            // Update the entropy stream by adding selected residuals as symbols to the
            // stream.
            for (int i = 0; i < num_components; ++i)
            {
                entropy_symbols_[i] = this.convertSignedIntToSymbol(best_prediction.residuals[i]);
            }
            
            entropy_tracker_.push(entropy_symbols_, num_components);
            
            for (int i = 0; i < num_parallelograms; ++i)
            {
                if ((0xff & best_prediction.configuration & (1 << i)) == 0)
                {
                    // Parallelogram not used, mark the edge as crease.
                    is_crease_edge_[num_parallelograms - 1].add(true);
                }
                else
                {
                    // Parallelogram used. Add it to the predicted value and mark the
                    // edge as not a crease.
                    is_crease_edge_[num_parallelograms - 1].add(false);
                }
                
            }
            
            this.transform_.computeCorrection(in_data.slice(dst_offset), IntSpan.wrap(best_prediction.predicted_value), out_corr.slice(dst_offset), 0);
        }
        
        // First element is always fixed because it cannot be predicted.
        for (int i = 0; i < num_components; ++i)
        {
            pred_vals[0][i] = 0;
        }
        
        this.transform_.computeCorrection(in_data, 0, IntSpan.wrap(pred_vals[0]), 0, out_corr, 0, 0);
    }
    
    /**
     *  Function used to compute number of bits needed to store overhead of the
     *  predictor. In this case, we consider overhead to be all bits that mark
     *  whether a parallelogram should be used for prediction or not. The input
     *  to this method is the total number of parallelograms that were evaluated so
     *  far(total_parallelogram), and the number of parallelograms we decided to
     *  use for prediction (total_used_parallelograms).
     *  Returns number of bits required to store the overhead.
     *
     */
    long computeOverheadBits(long total_used_parallelograms, long total_parallelogram)
    {
        double entropy = this.computeBinaryShannonEntropy((int)total_parallelogram, (int)total_used_parallelograms);
        
        // Round up to the nearest full bit.
        return (long)Math.ceil((double)total_parallelogram * entropy);
    }
    
    double computeBinaryShannonEntropy(int num_values, int num_true_values)
    {
        if (num_values == 0)
            return 0.0;
        
        // We can exit early if the data set has 0 entropy.
        if (num_true_values == 0 || (num_values == num_true_values))
            return 0.0;
        double true_freq = (double)num_true_values / (double)num_values;
        double false_freq = 1.0 - true_freq;
        return -(true_freq * (Math.log(true_freq) / Math.log(2)) + (false_freq * (Math.log(false_freq) / Math.log(2))));
    }
    
    // Computes error for predicting |predicted_val| instead of |actual_val|.
    // Error is computed as the number of bits needed to encode the difference
    // between the values.
    // 
    Error computeError(IntSpan predicted_val, IntSpan actual_val, IntSpan out_residuals, int num_components)
    {
        Error error = new Error();
        
        for (int i = 0; i < num_components; ++i)
        {
            int dif = predicted_val.get(i) - actual_val.get(i);
            error.residual_error += Math.abs(dif);
            out_residuals.put(i, dif);
            // Entropy needs unsigned symbols, so convert the signed difference to an
            // unsigned symbol.
            entropy_symbols_[i] = this.convertSignedIntToSymbol(dif);
        }
        
        ShannonEntropyTracker.EntropyData entropy_data = entropy_tracker_.peek(entropy_symbols_, num_components);
        
        error.num_bits = entropy_tracker_.getNumberOfDataBits(entropy_data) + entropy_tracker_.getNumberOfRAnsTableBits(entropy_data);
        return error;
    }
    
    /**
     *  Helper function that converts a single signed integer value into an unsigned
     *  integer symbol that can be encoded using an entropy encoder.
     *
     */
    int convertSignedIntToSymbol(int val)
    {
        // Early exit if val is positive.
        if (val >= 0)
            return val << 1;
        val = -(val + 1);
        // Map -1 to 0, -2 to -1, etc..
        int ret = val;
        ret <<= 1;
        ret |= 1;
        return ret;
    }
    
    /**
     *  Generates the next permutation of the boolean array in lexicographic order.
     *  Returns true if a next permutation exists, false otherwise.
     *
     */
    private boolean nextPermutation(BoolSpan array, int length)
    {
        int i = length - 2;
        while (i >= 0 && (!array.get(i) || array.get(i + 1)))
        {
            i--;
        }
        
        
        if (i < 0)
            return false;
        int j = length - 1;
        while (!array.get(j) || array.get(i))
        {
            j--;
        }
        
        boolean temp = array.get(i);
        array.put(i, array.get(j));
        array.put(j, temp);
        int left = i + 1;
        int right = length - 1;
        while (left < right)
        {
            temp = array.get(left);
            array.put(left, array.get(right));
            array.put(right, temp);
            left++;
            right--;
        }
        
        
        return true;
    }
    
    @Override
    public void decodePredictionData(DecoderBuffer buffer)
        throws DrakoException
    {
        
        if (buffer.getBitstreamVersion() < 22)
        {
            byte mode = buffer.decodeU8();
            
            if ((0xff & mode) != (byte)OPTIMAL_MULTI_PARALLELOGRAM)
                throw DracoUtils.failed();
        }
        
        
        // Encode selected edges using separate rans bit coder for each context.
        for (int i = 0; i < K_MAX_NUM_PARALLELOGRAMS; ++i)
        {
            int num_flags = Decoding.decodeVarintU32(buffer);
            if ((0xffffffffl & num_flags) > this.meshData.getCornerTable().getNumCorners())
                throw DracoUtils.failed();
            if ((0xffffffffl & num_flags) > 0)
            {
                is_crease_edge_[i] = new ArrayList<Boolean>(num_flags);
                RAnsBitDecoder decoder = new RAnsBitDecoder();
                decoder.startDecoding(buffer);
                for (int j = 0; j < (0xffffffffl & num_flags); ++j)
                {
                    is_crease_edge_[i].add(decoder.decodeNextBit());
                }
                
                decoder.endDecoding();
            }
            
        }
        
        super.decodePredictionData(buffer);
    }
    
    @Override
    public void computeOriginalValues(IntSpan in_corr, IntSpan out_data, int size, int num_components, int[] entry_to_point_id_map)
        throws DrakoException
    {
        this.transform_.initializeDecoding(num_components);
        int[][] pred_vals = new int[K_MAX_NUM_PARALLELOGRAMS][];
        for (int i = 0; i < K_MAX_NUM_PARALLELOGRAMS; ++i)
        {
            pred_vals[i] = new int[num_components];
        }
        
        this.transform_.computeOriginalValue(IntSpan.wrap(pred_vals[0]), in_corr, out_data);
        ICornerTable table = this.meshData.getCornerTable();
        int[] vertex_to_data_map = this.meshData.vertexToDataMap;
        int[] is_crease_edge_pos = new int[K_MAX_NUM_PARALLELOGRAMS];
        int[] multi_pred_vals = new int[num_components];
        int corner_map_size = this.meshData.dataToCornerMap.getCount();
        for (int p = 1; p < corner_map_size; ++p)
        {
            int start_corner_id = this.meshData.dataToCornerMap.get(p);
            int corner_id = start_corner_id;
            int num_parallelograms = 0;
            boolean first_pass = true;
            while (corner_id != CornerTable.K_INVALID_CORNER_INDEX)
            {
                if (MeshPredictionSchemeParallelogram.computeParallelogramPrediction(p, corner_id, table, vertex_to_data_map, out_data, num_components, IntSpan.wrap(pred_vals[num_parallelograms])))
                {
                    // Parallelogram prediction applied and stored in
                    // |pred_vals[num_parallelograms]|
                    ++num_parallelograms;
                    // Stop processing when we reach the maximum number of allowed
                    // parallelograms.
                    if (num_parallelograms == K_MAX_NUM_PARALLELOGRAMS)
                        break;
                }
                
                
                // Proceed to the next corner attached to the vertex. First swing left
                // and if we reach a boundary, swing right from the start corner.
                if (first_pass)
                {
                    corner_id = table.swingLeft(corner_id);
                }
                else
                {
                    corner_id = table.swingRight(corner_id);
                }
                
                if (corner_id == start_corner_id)
                    break;
                if (corner_id == CornerTable.K_INVALID_CORNER_INDEX && first_pass)
                {
                    first_pass = false;
                    corner_id = table.swingRight(start_corner_id);
                }
                
            }
            
            int num_used_parallelograms = 0;
            if (num_parallelograms > 0)
            {
                for (int i = 0; i < num_components; ++i)
                {
                    multi_pred_vals[i] = 0;
                }
                
                // Check which parallelograms are actually used.
                for (int i = 0; i < num_parallelograms; ++i)
                {
                    int context = num_parallelograms - 1;
                    int pos = is_crease_edge_pos[context]++;
                    if (is_crease_edge_[context].size() <= pos)
                        throw DracoUtils.failed();
                    boolean is_crease = is_crease_edge_[context].get(pos);
                    if (!is_crease)
                    {
                        ++num_used_parallelograms;
                        for (int j = 0; j < num_components; ++j)
                        {
                            multi_pred_vals[j] = (int)((long)(multi_pred_vals[j]) + (long)(pred_vals[i][j]));
                        }
                        
                    }
                    
                }
                
            }
            
            int dst_offset = p * num_components;
            if (num_used_parallelograms == 0)
            {
                int src_offset = (p - 1) * num_components;
                this.transform_.computeOriginalValue(out_data.slice(src_offset), in_corr.slice(dst_offset), out_data.slice(dst_offset));
            }
            else
            {
                // Compute the correction from the predicted value.
                for (int c = 0; c < num_components; ++c)
                {
                    multi_pred_vals[c] /= num_used_parallelograms;
                }
                
                this.transform_.computeOriginalValue(IntSpan.wrap(multi_pred_vals), in_corr.slice(dst_offset), out_data.slice(dst_offset));
            }
            
        }
        
    }
    
    private void $initFields$()
    {
        try
        {
            is_crease_edge_ = new ArrayList[K_MAX_NUM_PARALLELOGRAMS];
            entropy_tracker_ = new ShannonEntropyTracker();
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
        
    }
    
}
