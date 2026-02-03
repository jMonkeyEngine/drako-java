package com.openize.drako;
import com.openize.drako.HashBuilder;
import com.openize.drako.Struct;
import java.io.Serializable;
/**
 *  Struct for holding error information about prediction.
 *
 */
final class Error implements Struct<Error>, Serializable
{    
    public long residual_error;
    public long num_bits;
    public static boolean op_lt(Error a, Error b)
    {
        if (a.num_bits != b.num_bits)
            return a.num_bits < b.num_bits;
        return a.residual_error < b.residual_error;
    }
    
    public static boolean op_gt(Error a, Error b)
    {
        if (a.num_bits != b.num_bits)
            return a.num_bits > b.num_bits;
        return a.residual_error > b.residual_error;
    }
    
    public Error()
    {
    }
    
    private Error(Error other)
    {
        this.residual_error = other.residual_error;
        this.num_bits = other.num_bits;
    }
    
    @Override
    public Error clone()
    {
        return new Error(this);
    }
    
    @Override
    public void copyFrom(Error src)
    {
        if (src == null)
            return;
        this.residual_error = src.residual_error;
        this.num_bits = src.num_bits;
    }
    
    static final long serialVersionUID = 2253L;
    @Override
    public int hashCode()
    {
        HashBuilder builder = new HashBuilder();
        builder.hash(this.residual_error);
        builder.hash(this.num_bits);
        return builder.hashCode();
    }
    
    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof Error))
            return false;
        Error rhs = (Error)obj;
        if (this.residual_error != rhs.residual_error)
            return false;
        if (this.num_bits != rhs.num_bits)
            return false;
        return true;
    }
    
}
