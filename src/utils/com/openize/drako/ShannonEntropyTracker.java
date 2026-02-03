package com.openize.drako;
import com.openize.drako.HashBuilder;
import com.openize.drako.Struct;
import java.io.Serializable;
import java.util.ArrayList;
/**
 *  Class that can be used to keep track of the Shannon entropy on streamed data.
 *  As new symbols are pushed to the tracker, the entropy is automatically
 *  recomputed. The class also support recomputing the entropy without actually
 *  pushing the symbols to the tracker through the Peek() method.
 *
 */
public class ShannonEntropyTracker
{    
    /**
     *  Struct for holding entropy data about the symbols added to the tracker.
     *  It can be used to compute the number of bits needed to store the data using
     *  the method:
     *    ShannonEntropyTracker.GetNumberOfDataBits(entropy_data);
     *  or to compute the approximate size of the frequency table needed by the
     *  rans coding using method:
     *    ShannonEntropyTracker.GetNumberOfRAnsTableBits(entropy_data);
     *
     */
    public static final class EntropyData implements Struct<EntropyData>, Serializable
    {
        public double entropy_norm;
        public int num_values;
        public int max_symbol;
        public int num_unique_symbols;
        public EntropyData(double entropyNorm, int numValues, int maxSymbol, int numUniqueSymbols)
        {
            this.entropy_norm = entropyNorm;
            this.num_values = numValues;
            this.max_symbol = maxSymbol;
            this.num_unique_symbols = numUniqueSymbols;
        }
        
        public static EntropyData getDefault()
        {
            return new EntropyData(0.0, 0, 0, 0);
        }
        
        public EntropyData()
        {
        }
        
        private EntropyData(EntropyData other)
        {
            this.entropy_norm = other.entropy_norm;
            this.num_values = other.num_values;
            this.max_symbol = other.max_symbol;
            this.num_unique_symbols = other.num_unique_symbols;
        }
        
        @Override
        public EntropyData clone()
        {
            return new EntropyData(this);
        }
        
        @Override
        public void copyFrom(EntropyData src)
        {
            if (src == null)
                return;
            this.entropy_norm = src.entropy_norm;
            this.num_values = src.num_values;
            this.max_symbol = src.max_symbol;
            this.num_unique_symbols = src.num_unique_symbols;
        }
        
        static final long serialVersionUID = 67115080L;
        @Override
        public int hashCode()
        {
            HashBuilder builder = new HashBuilder();
            builder.hash(this.entropy_norm);
            builder.hash(this.num_values);
            builder.hash(this.max_symbol);
            builder.hash(this.num_unique_symbols);
            return builder.hashCode();
        }
        
        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof EntropyData))
                return false;
            EntropyData rhs = (EntropyData)obj;
            if (this.entropy_norm != rhs.entropy_norm)
                return false;
            if (this.num_values != rhs.num_values)
                return false;
            if (this.max_symbol != rhs.max_symbol)
                return false;
            if (this.num_unique_symbols != rhs.num_unique_symbols)
                return false;
            return true;
        }
        
    }
    
    private ArrayList<Integer> frequencies_;
    private EntropyData entropy_data_ = new EntropyData();
    public ShannonEntropyTracker()
    {
        this.frequencies_ = new ArrayList<Integer>();
        this.entropy_data_.copyFrom(EntropyData.getDefault());
    }
    
    /**
     *  Adds new symbols to the tracker and recomputes the entropy accordingly.
     *
     */
    public EntropyData push(int[] symbols, int num_symbols)
    {
        return this.updateSymbols(symbols, num_symbols, true);
    }
    
    /**
     *  Returns new entropy data for the tracker as if |symbols| were added to the
     *  tracker without actually changing the status of the tracker.
     *
     */
    public EntropyData peek(int[] symbols, int num_symbols)
    {
        return this.updateSymbols(symbols, num_symbols, false);
    }
    
    /**
     *  Gets the number of bits needed for encoding symbols added to the tracker.
     *
     */
    public long getNumberOfDataBits()
    {
        return this.getNumberOfDataBits(entropy_data_);
    }
    
    /**
     *  Gets the number of bits needed for encoding frequency table using the rans
     *  encoder.
     *
     */
    public long getNumberOfRAnsTableBits()
    {
        return this.getNumberOfRAnsTableBits(entropy_data_);
    }
    
    /**
     *  Gets the number of bits needed for encoding given |entropy_data|.
     *
     */
    public long getNumberOfDataBits(EntropyData entropy_data)
    {
        if (entropy_data.num_values < 2)
            return 0L;
        // We need to compute the number of bits required to represent the stream
        // using the entropy norm. Note that:
        //
        //   entropy = log2(num_values) - entropy_norm / num_values
        //
        // and number of bits required for the entropy is: num_values * entropy
        //
        return (long)Math.ceil(entropy_data.num_values * (Math.log(entropy_data.num_values) / Math.log(2)) - entropy_data.entropy_norm);
    }
    
    /**
     *  Gets the number of bits needed for encoding frequency table using the rans
     *  encoder for the given |entropy_data|.
     *
     */
    public long getNumberOfRAnsTableBits(EntropyData entropy_data)
    {
        return ShannonEntropyTracker.approximateRAnsFrequencyTableBits(entropy_data.max_symbol + 1, entropy_data.num_unique_symbols);
    }
    
    private EntropyData updateSymbols(int[] symbols, int num_symbols, boolean push_changes)
    {
        EntropyData ret_data = Struct.byVal(entropy_data_);
        ret_data.num_values += num_symbols;
        
        for (int i = 0; i < num_symbols; ++i)
        {
            int symbol = symbols[i];
            
            // Ensure the frequencies list is large enough
            while (frequencies_.size() <= (0xffffffffl & symbol))
            {
                frequencies_.add(0);
            }
            
            double old_symbol_entropy_norm = 0.0;
            int frequency = frequencies_.get(symbol);
            
            if (frequency > 1)
            {
                old_symbol_entropy_norm = frequency * (Math.log(frequency) / Math.log(2));
            }
            else if (frequency == 0)
            {
                ret_data.num_unique_symbols++;
                if ((0xffffffffl & symbol) > ret_data.max_symbol)
                {
                    ret_data.max_symbol = symbol;
                }
                
            }
            
            
            frequency++;
            frequencies_.set(symbol, frequency);
            double new_symbol_entropy_norm = frequency * (Math.log(frequency) / Math.log(2));
            
            // Update the final entropy.
            ret_data.entropy_norm += new_symbol_entropy_norm - old_symbol_entropy_norm;
        }
        
        
        if (push_changes)
        {
            // Update entropy data of the stream.
            this.entropy_data_.copyFrom(ret_data);
        }
        else
        {
            // We are only peeking so do not update the stream.
            // Revert changes in the frequency table.
            for (int i = 0; i < num_symbols; ++i)
            {
                int symbol = symbols[i];
                frequencies_.set(symbol, frequencies_.get(symbol) - 1);
            }
            
        }
        
        
        return ret_data;
    }
    
    /**
     *  Compute approximate frequency table size needed for storing the provided
     *  symbols.
     *
     */
    private static long approximateRAnsFrequencyTableBits(int max_value, int num_unique_symbols)
    {
        long table_zero_frequency_bits = 8 * (num_unique_symbols + ((max_value - num_unique_symbols) / 64));
        return 8 * num_unique_symbols + table_zero_frequency_bits;
    }
    
}
