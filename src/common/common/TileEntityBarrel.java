package factorization.common;

import java.io.DataInput;
import java.io.IOException;

import net.minecraft.src.Block;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.FactorizationHack;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.Packet;
import factorization.common.NetworkFactorization.MessageType;

//Based off of technology found stockpiled in the long-abandoned dwarven fortress of "Nod Semor, the Toad of Unity".

//Special hack: If the item can stack && we have > 1, the input slot shall be split into 2 half stacks.
//If the item doesn't stack, then the input slot'll have stacksize = 0
//NO! We *can* have for the stackable items case have 1, and 1 even if there's less than 2 itemCount. Just clear it after the first one is emptied

public class TileEntityBarrel extends TileEntityFactorization {

    static final int maxBarrelSize = 1024 * 64;
    // EMC of TNT is 964.
    static final int explosionStackSize = 64; // how many stacks required for an explosion; depends on item.maxStackSize
    static final float explosionStrength = 2.5F; //explosion base strength
    static final float explosionStrengthMin = 1.0F; //if items don't stack very high, explosion strength will be weakened

    // save these guys
    public ItemStack item;
    private ItemStack topStack; //always 0 unless nearly full
    private int middleCount;
    private ItemStack bottomStack; //always full unless nearly empty

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.BARREL;
    }

    @Override
    public BlockClass getBlockClass() {
        return BlockClass.Barrel;
    }

    //These are some core barrel item-count manipulating functions.

    /**
     * @return # of items in barrel, including the top/bottom stacks.
     */
    public int getItemCount() {
        if (item == null) {
            return 0;
        }
        if (topStack == null || !itemMatch(topStack)) {
            topStack = item.copy();
            topStack.stackSize = 0;
        }
        if (bottomStack == null || !itemMatch(bottomStack)) {
            bottomStack = item.copy();
            bottomStack.stackSize = 0;
        }
        return topStack.stackSize + middleCount + bottomStack.stackSize;
    }

    /**
     * redistribute count to the item stacks.
     */
    public void updateStacks() {
        if (item == null) {
            topStack = bottomStack = null;
            middleCount = 0;
            return;
        }
        int count = getItemCount();
        if (count == 0) {
            topStack = bottomStack = item = null;
            middleCount = 0;
            return;
        }
        int upperLine = maxBarrelSize - item.getMaxStackSize();
        if (count > upperLine) {
            topStack = item.copy();
            topStack.stackSize = count - upperLine;
            count -= topStack.stackSize;
        }
        else {
            topStack.stackSize = 0;
        }
        bottomStack.stackSize = Math.min(item.getMaxStackSize(), count);
        count -= bottomStack.stackSize;
        middleCount = count;
    }

    public void changeItemCount(int delta) {
        middleCount = getItemCount() + delta;
        if (!(middleCount >= 0 && middleCount < maxBarrelSize)) {
            throw new Error("tried changing item count to out of range");
        }
        topStack = bottomStack = null;
        updateStacks();
        broadcastItemCount();
    }

    public void setItemCount(int val) {
        topStack = bottomStack = null;
        middleCount = val;
        changeItemCount(0);
    }

    private ItemStack makeStack(int count) {
        if (item == null) {
            throw new Error();
        }
        ItemStack ret = item.copy();
        ret.stackSize = count;
        assert ret.stackSize > 0 && ret.stackSize <= item.getMaxStackSize();
        return ret;
    }

    //end hard-core count manipulation
    @Override
    public int getSizeInventory() {
        // Top is the input slot; if we're typed it's an IS of size 0; if it's full, then it's however more will fit.
        // Bottom is the output slot; always as full as possible
        return 2;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (item == null) {
            cleanBarrel();
            return null;
        }
        if (slot == 0) {
            return bottomStack;
        }
        if (slot == 1) {
            return topStack;
        }
        return null;
    }

    static int sizeOf(ItemStack is) {
        if (is == null) {
            return 0;
        }
        return is.stackSize;
    }

    private boolean itemMatch(ItemStack is) {
        if (is == null || item == null) {
            return false;
        }
        item.stackSize = is.stackSize;
        return ItemStack.areItemStacksEqual(item, is);
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack is = getStackInSlot(slot);
        if (is == null) {
            return null;
        }
        ItemStack ret = is.splitStack(amount);
        updateStacks();
        broadcastItemCount();
        return ret;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack is) {
        ItemStack old_item = item;
        taintBarrel(is);
        if (is != null && !itemMatch(is)) {
            //whoever's doing this is a douchebag. Forget about the item.
            new Exception().printStackTrace();
            return;
        }
        switch (slot) {
        case 0:
            bottomStack = is;
            break;
        case 1:
            topStack = is;
            break;
        }
        if (old_item != item) {
            broadcastItem();
        }
        broadcastItemCount();
    }

    @Override
    public String getInvName() {
        return "Barrel";
    }

    @Override
    public int getStartInventorySide(int side) {
        switch (side) {
        case 0:
            return 0; // -Y
        case 1:
            return 1; // +y
        default:
            return -1;
        }
    }

    @Override
    public int getSizeInventorySide(int side) {
        if (side == 0 || side == 1) {
            return 1;
        }
        return 0;
    }

    void info(EntityPlayer entityplayer) {
        if (item == null && getItemCount() == 0) {
            entityplayer.addChatMessage("This barrel is empty");
        } else if (getItemCount() >= maxBarrelSize) {
            Core.instance.broadcastTranslate(entityplayer, "This barrel is full of %s", Core.instance.translateItemStack(item));
        } else {
            Core.instance.broadcastTranslate(entityplayer, "This barrel contains %s %s", "" + getItemCount(), Core.instance.translateItemStack(item));
        }
    }

    void taintBarrel(ItemStack is) {
        if (is == null) {
            return;
        }
        if (getItemCount() != 0) {
            return;
        }
        if (is.getMaxStackSize() >= maxBarrelSize) {
            return;
        }
        item = is.copy();
        broadcastItem();
    }

    void broadcastItem() {
        if (worldObj != null && Core.instance.isCannonical(worldObj)) {
            Core.network.broadcastMessage(null, getCoord(), MessageType.BarrelItem, item);
        }
    }

    void broadcastItemCount() {
        if (worldObj != null && Core.instance.isCannonical(worldObj)) {
            Core.network.broadcastMessage(null, getCoord(), MessageType.BarrelCount, getItemCount());
        }
    }

    void cleanBarrel() {
        if (getItemCount() == 0) {
            topStack = bottomStack = item = null;
            middleCount = 0;
        } else {
            assert item != null;
        }
    }

    long lastClick = -1000; //NOTE: This really should be player-specific!

    //* 			Left-Click		Right-Click
    //* No Shift:	Remove stack	Add item
    //* Shift:		Remove 1 item	Use item
    //* Double:						Add all but 1 item

    @Override
    public void activate(EntityPlayer entityplayer) {
        // right click: put an item in
        if (!Core.instance.isCannonical(entityplayer.worldObj)) {
            return;
        }
        if (worldObj.getWorldTime() - lastClick < 10 && item != null) {
            addAllItems(entityplayer);
            return;
        }
        lastClick = worldObj.getWorldTime();
        int handslot = entityplayer.inventory.currentItem;
        if (handslot < 0 || handslot > 8) {
            return;
        }

        ItemStack is = entityplayer.inventory.getStackInSlot(handslot);
        if (is == null) {
            info(entityplayer);
            return;
        }

        if (is.isItemDamaged()) {
            if (getItemCount() == 0) {
                entityplayer.addChatMessage("Damaged items can not be stored");
            } else {
                info(entityplayer);
            }
            return;
        }

        taintBarrel(is);

        if (!itemMatch(is)) {
            if (Core.instance.translateItemStack(is).equals(Core.instance.translateItemStack(item))) {
                entityplayer.addChatMessage("That item is different");
            } else {
                info(entityplayer);
            }
            return;
        }
        int free = maxBarrelSize - getItemCount();
        if (free <= 0) {
            info(entityplayer);
            return;
        }
        int take = Math.min(free, is.stackSize);
        is.stackSize -= take;
        changeItemCount(take);
        if (is.stackSize == 0) {
            entityplayer.inventory.setInventorySlotContents(handslot, null);
        }
    }

    @Override
    public void click(EntityPlayer entityplayer) {
        // left click: remove a stack
        if (!Core.instance.isCannonical(entityplayer.worldObj)) {
            return;
        }
        if (getItemCount() == 0 || item == null) {
            info(entityplayer);
            return;
        }
        int to_remove = Math.min(item.getMaxStackSize(), getItemCount());
        if (entityplayer.isSneaking() && to_remove >= 1) {
            to_remove = 1;
        }
        ejectItem(makeStack(to_remove), false, entityplayer);
        changeItemCount(-to_remove);
        cleanBarrel();
    }

    void addAllItems(EntityPlayer entityplayer) {
        ItemStack hand = entityplayer.inventory.getStackInSlot(entityplayer.inventory.currentItem);
        if (hand != null) {
            taintBarrel(hand);
        }
        if (hand != null && !itemMatch(hand)) {
            if (Core.instance.translateItemStack(hand).equals(Core.instance.translateItemStack(item))) {
                entityplayer.addChatMessage("That item is different");
            } else {
                info(entityplayer);
            }
            return;
        }
        InventoryPlayer inv = entityplayer.inventory;
        int total_delta = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            int free_space = maxBarrelSize - (getItemCount() + total_delta);
            if (free_space <= 0) {
                break;
            }
            ItemStack is = inv.getStackInSlot(i);
            if (is == null || is.stackSize <= 0) {
                continue;
            }
            if (!itemMatch(is)) {
                continue;
            }
            int toAdd = Math.min(is.stackSize, free_space);
            if (is == hand && toAdd > 1) {
                toAdd -= 1;
            }
            total_delta += toAdd;
            is.stackSize -= toAdd;
            if (is.stackSize <= 0) {
                inv.setInventorySlotContents(i, null);
            }
        }
        changeItemCount(total_delta);
        if (total_delta > 0) {
            Core.instance.updatePlayerInventory(entityplayer);
        }
    }

    public boolean canExplode() {
        return getItemCount() > explosionStackSize * item.getMaxStackSize();
    }

    public boolean flamingExplosion() {
        if (item.getItem() == Item.gunpowder || item.itemID == Block.tnt.blockID) {
            return true;
        }
        return getItemCount() > (maxBarrelSize / 2);
    }

    @Override
    public void dropContents() {
        if (item == null || getItemCount() <= 0) {
            return;
        }
        // If too big, explode. Explode before dropping the items so that some will survive.
        if (canExplode()) {
            float str = explosionStrength;
            if (getItemCount() > (maxBarrelSize * 2 / 3)) {
                str *= 3;
            } else if (getItemCount() > (maxBarrelSize * 1 / 2)) {
                str *= 2;
            } else {
                // compensate for items that don't stack
                int sword_compensate = Math.min(item.getMaxStackSize(), 64);
                str *= sword_compensate / 64F;
                str = Math.max(str, explosionStrengthMin); // but not too much
            }
            if (item.getItem() == Item.gunpowder || item.itemID == Block.tnt.blockID) {
                str *= 4;
                //I have *no* idea how strong this is...
            }
            worldObj.newExplosion(null, xCoord, yCoord, zCoord, str, flamingExplosion());
        }
        // not all items will be dropped if it's big enough to blow up.
        // This replaces lag-o-death with explosion-o-death
        int count = getItemCount();
        for (int i = 0; i < explosionStackSize; i++) {
            int to_drop;
            to_drop = Math.min(item.getMaxStackSize(), count);
            count -= to_drop;
            ejectItem(makeStack(to_drop), getItemCount() > 64 * 16, null);
        }
        topStack = null;
        middleCount = 0;
        bottomStack = null;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        saveItem("item_type", tag, item);
        tag.setInteger("item_count", getItemCount());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        item = readItem("item_type", tag);
        setItemCount(tag.getInteger("item_count"));
    }

    @Override
    void sendFullDescription(EntityPlayer player) {
        super.sendFullDescription(player);
        broadcastItem();
        changeItemCount(0);
    }

    @Override
    public Packet getDescriptionPacket() {
        if (item == null) {
            return super.getDescriptionPacket();
        }
        return getDescriptionPacketWith(MessageType.BarrelDescription, getItemCount(), item);
    }

    @Override
    void doLogic() {
    }

    @Override
    public boolean canUpdate() {
        //XXX TODO: Barrels don't need this. (Just to make sure the MD is enforced, since an incorrect MD'd be so dangerous)
        //Can probably get rid of it in... well, several versions. Maybe in September?
        return true;
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        updateStacks();
    }

    @Override
    public boolean handleMessageFromServer(int messageType, DataInput input) throws IOException {
        if (super.handleMessageFromServer(messageType, input)) {
            return true;
        }
        switch (messageType) {
        case MessageType.BarrelCount:
            setItemCount(input.readInt());
            break;
        case MessageType.BarrelDescription:
            int i = input.readInt();
            item = FactorizationHack.loadItemStackFromDataInput(input);
            setItemCount(i);
            break;
        case MessageType.BarrelItem:
            item = FactorizationHack.loadItemStackFromDataInput(input);
            break;
        default:
            return false;
        }
        return true;
    }
}
