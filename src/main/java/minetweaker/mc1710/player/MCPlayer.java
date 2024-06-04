/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package minetweaker.mc1710.player;

import minetweaker.MineTweakerAPI;
import minetweaker.api.chat.IChatMessage;
import minetweaker.api.data.IData;
import minetweaker.api.item.IItemStack;
import minetweaker.api.minecraft.MineTweakerMC;
import minetweaker.api.player.IPlayer;
import minetweaker.mc1710.MineTweakerConfig;
import minetweaker.mc1710.MineTweakerMod;
import minetweaker.mc1710.data.NBTConverter;
import minetweaker.mc1710.network.MineTweakerCopyClipboardPacket;
import minetweaker.mc1710.network.MineTweakerOpenBrowserPacket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @author Stan
 */
public class MCPlayer implements IPlayer {
	private final WeakReference<EntityPlayer> player;

	public MCPlayer(EntityPlayer player) {
		this.player = new WeakReference<>(player);
	}

	public EntityPlayer getInternal() {
		return player.get();
	}

	private <T> T ifPresent(Function<EntityPlayer, T> fn, T defaultValue) {
		EntityPlayer plr = player.get();
		if (plr != null) {
			return fn.apply(plr);
		}
		return defaultValue;
	}

	private void ifPresent(Consumer<EntityPlayer> fn) {
		EntityPlayer plr = player.get();
		if (plr != null) {
			fn.accept(plr);
		}
	}

	@Override
	public String getId() {
		return null; // TODO: we should be having this for MC 1.7.10, right?
	}

	@Override
	public String getName() {
		return ifPresent(EntityPlayer::getCommandSenderName, "MISSING");
	}

	@Override
	public IData getData() {
		return ifPresent(player -> NBTConverter.from(player.getEntityData(), true), null);
	}

    @Override
    public int getXP() {
        return ifPresent(player -> player.experienceLevel, 0);
    }

    @Override
    public void setXP(int xp) {
		ifPresent(player -> {
        	player.experienceLevel = 0;
        	player.addExperienceLevel(xp);
		});
    }

    @Override
    public void removeXP(int xp) {
		ifPresent(player -> {
			final int newLvl = Math.max(0, player.experienceLevel - xp);
			player.experienceLevel = 0;
			player.addExperienceLevel(newLvl);
		});
    }

    @Override
	public void update(IData data) {
		ifPresent(player -> {
			NBTConverter.updateMap(player.getEntityData(), data);
		});
	}

	@Override
	public void sendChat(IChatMessage message) {
		ifPresent(player -> {
			Object internal = message.getInternal();
			if (!(internal instanceof IChatComponent)) {
				MineTweakerAPI.logError("not a valid chat message");
				return;
			}
			player.addChatMessage((IChatComponent) internal);
		});
	}

	@Override
	public void sendChat(String msg) {
		ifPresent(player -> {
			String message = msg;
			if (message.length() > MAX_CHAT_MESSAGE_LENGTH)
			{
				message = message.substring(0, MAX_CHAT_MESSAGE_LENGTH);
			}
			player.addChatMessage(new ChatComponentText(message));
		});
	}

	@Override
	public int getHotbarSize() {
		return ifPresent(p -> 9, 0);
	}

	@Override
	public IItemStack getHotbarStack(int i) {
		return ifPresent(player -> i < 0 || i >= 9 ? null : MineTweakerMC.getIItemStack(player.inventory.getStackInSlot(i)), null);
	}

	@Override
	public int getInventorySize() {
		return ifPresent(player -> player.inventory.getSizeInventory(), 0);
	}

	@Override
	public IItemStack getInventoryStack(int i) {
		return ifPresent(player -> MineTweakerMC.getIItemStack(player.inventory.getStackInSlot(i)), null);
	}

	@Override
	public IItemStack getCurrentItem() {
		return ifPresent(player -> MineTweakerMC.getIItemStack(player.getCurrentEquippedItem()), null);
	}

	@Override
	public boolean isCreative() {
		return ifPresent(player -> player.capabilities.isCreativeMode, false);
	}

	@Override
	public boolean isAdventure() {
		return ifPresent(player -> !player.capabilities.allowEdit, false);
	}

	@Override
	public void openBrowser(String url) {
		ifPresent(player -> {
			if (player instanceof EntityPlayerMP && MineTweakerConfig.handleDesktopPackets) {
				MineTweakerMod.NETWORK.sendTo(
						new MineTweakerOpenBrowserPacket(url),
						(EntityPlayerMP) player);
			}
		});
	}

	@Override
	public void copyToClipboard(String value) {
		ifPresent(player -> {
			if (player instanceof EntityPlayerMP && MineTweakerConfig.handleDesktopPackets) {
				MineTweakerMod.NETWORK.sendTo(
						new MineTweakerCopyClipboardPacket(value),
						(EntityPlayerMP) player);
			}
		});
	}

	@Override
	public boolean equals(Object other) {
		if (other.getClass() != this.getClass())
			return false;
		MCPlayer o = (MCPlayer) other;
		return ifPresent(p1 -> o.ifPresent(p2 -> p1 == p2, false), false);
	}

	private volatile Integer hash;

	@Override
	public int hashCode() {
		if (hash == null) {
			synchronized (this) {
				if (hash == null) {
					hash = ifPresent(player -> {
						int h = 5;
						h = 23 * h + player.hashCode();
						return h;
					}, 0);
				}
			}
		}
		return hash;
	}

	@Override
	public void give(IItemStack stack) {
		ifPresent(player -> player.inventory.addItemStackToInventory(MineTweakerMC.getItemStack(stack).copy()));
	}

	@Override
	public boolean brokenReference() {
		return ifPresent(p -> false, true);
	}
}
