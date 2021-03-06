package factorization.api.datahelpers;

import java.io.DataOutput;
import java.io.IOException;

import factorization.util.DataUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import cpw.mods.fml.relauncher.Side;

public class DataOutPacket extends DataHelper {
    private final DataOutput dos;
    private final Side side;
    
    public DataOutPacket(DataOutput dos, Side side) {
        this.dos = dos;
        this.side = side;
    }

    @Override
    protected boolean shouldStore(Share share) {
        return share.is_public;
    }
    
    @Override
    public boolean isReader() {
        return false;
    }

    @Override
    public boolean putBoolean(boolean value) throws IOException {
        dos.writeBoolean(value);
        return value;
    }

    @Override
    public byte putByte(byte value) throws IOException {
        dos.writeByte(value);
        return value;
    }

    @Override
    public short putShort(short value) throws IOException {
        dos.writeShort(value);
        return value;
    }

    @Override
    public int putInt(int value) throws IOException {
        dos.writeInt(value);
        return value;
    }

    @Override
    public long putLong(long value) throws IOException {
        dos.writeLong(value);
        return value;
    }

    @Override
    public float putFloat(float value) throws IOException {
        dos.writeFloat(value);
        return value;
    }

    @Override
    public double putDouble(double value) throws IOException {
        dos.writeDouble(value);
        return value;
    }

    @Override
    public String putString(String value) throws IOException {
        dos.writeUTF(value);
        return value;
    }

    @Override
    public NBTTagCompound putTag(NBTTagCompound value) throws IOException {
        CompressedStreamTools.write(value, dos);
        return value;
    }

    @Override
    public ItemStack[] putItemArray(ItemStack[] value) throws IOException {
        for (ItemStack is : value) {
            if (is == null) is = DataUtil.NULL_ITEM;
            NBTTagCompound out = is.writeToNBT(new NBTTagCompound());
            CompressedStreamTools.write(out, dos);
        }
        return value;
    }

    @Override
    public int[] putIntArray(int[] value) throws IOException {
        dos.writeInt(value.length);
        for (int v : value) {
            dos.writeInt(v);
        }
        return value;
    }
}
