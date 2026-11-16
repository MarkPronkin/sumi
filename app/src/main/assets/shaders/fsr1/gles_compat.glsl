// ffx_a.h declares unused bitfield helpers which otherwise need GLES 3.1.
// Equivalent GLES 3.0 operations let the complete reference header compile.
uint bitfieldExtract(uint value,int offset,int bits) {
    if(bits==0) return 0u;
    if(bits==32) return value;
    return (value>>uint(offset)) & ((1u<<uint(bits))-1u);
}
uint bitfieldInsert(uint base,uint insert,int offset,int bits) {
    if(bits==0) return base;
    if(bits==32) return insert;
    uint mask=((1u<<uint(bits))-1u)<<uint(offset);
    return (base & ~mask) | ((insert<<uint(offset)) & mask);
}
