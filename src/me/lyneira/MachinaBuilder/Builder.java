package me.lyneira.MachinaBuilder;

import java.util.List;

import me.lyneira.MachinaCraft.BlockData;
import me.lyneira.MachinaCraft.BlockLocation;
import me.lyneira.MachinaCraft.BlockRotation;
import me.lyneira.MachinaCraft.BlockVector;
import me.lyneira.MachinaCraft.Fuel;
import me.lyneira.MachinaCraft.HeartBeatEvent;
import me.lyneira.MachinaCraft.Movable;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class Builder extends Movable {
	/**
	 * The number of server ticks to wait for a move action.
	 */
	private static final int moveDelay = 20;

	/**
	 * The number of server ticks to wait for a build action.
	 */
	private static final int buildDelay = 10;

	/**
	 * The maximum depth to which the Builder will drop blocks.
	 */
	private static final int maxDepth = 6;

	/**
	 * The amount of energy stored. This is just the number of server ticks left
	 * before needing to consume new fuel.
	 */
	private int currentEnergy = 0;

	/**
	 * True if the Builder should move during the next action.
	 */
	private boolean nextMove = false;

	/**
	 * Creates a new drill.
	 * 
	 * @param plugin
	 *            The MachinaCraft plugin
	 * @param anchor
	 *            The anchor location of the drill
	 * @param yaw
	 *            The direction of the drill
	 * @param moduleIndices
	 *            The active modules for the drill
	 */
	Builder(final Blueprint blueprint, Player player, BlockLocation anchor,
			final BlockRotation yaw, final List<Integer> moduleIndices) {
		super(blueprint, player, yaw, moduleIndices);

		this.player = player;
		// Set furnace to burning state.
		Block furnace = anchor.getRelative(
				blueprint.getByIndex(Blueprint.furnaceIndex, yaw,
						Blueprint.mainModuleIndex)).getBlock();
		Inventory inventory = ((Furnace) furnace.getState()).getInventory();
		Fuel.setFurnace(furnace, yaw.getOpposite().getFacing(), true, inventory);
	}

	/**
	 * Initiates the current move or build action.
	 */
	public HeartBeatEvent heartBeat(final BlockLocation anchor) {
		if (nextMove) {
			BlockLocation newAnchor = doMove(anchor);
			if (newAnchor == null) {
				return null;
			}
			return new HeartBeatEvent(queueAction(newAnchor), newAnchor);
		} else if (doBuild(anchor)) {
			return new HeartBeatEvent(queueAction(anchor));
		}
		return null;
	}

	/**
	 * Determines the delay for the next action.
	 * 
	 * @param anchor
	 *            The anchor of the Builder
	 * @return Delay in server ticks for the next action
	 */
	private int queueAction(final BlockLocation anchor) {
		if (nextBuild(anchor) == null) {
			nextMove = true;
			return moveDelay;
		} else {
			nextMove = false;
			return buildDelay;
		}
	}

	/**
	 * Drops a block below the head of the Builder.
	 * 
	 * @param anchor
	 *            The anchor of the Builder
	 * @return False if there was no energy or blocks left to complete the build
	 *         action. True if successful or no location is available to build
	 *         on.
	 */
	private boolean doBuild(final BlockLocation anchor) {
		BlockLocation nextBuild = nextBuild(anchor);
		if (nextBuild == null) {
			return true;
		} else {
			Block chestBlock = anchor.getRelative(
					blueprint.getByIndex(Blueprint.containerIndex, yaw,
							Blueprint.mainModuleIndex)).getBlock();
			Inventory inventory = ((Chest) chestBlock.getState())
					.getInventory();
			ItemStack[] contents = inventory.getContents();
			for (int i = 0; i < contents.length; i++) {
				ItemStack stack = contents[i];
				int typeId;
				if (contents[i] != null
						&& BlockData.isSolid(typeId = stack.getTypeId())) {
					// Simulate a build action
					if (!canPlace(nextBuild, typeId,
							nextBuild.getRelative(BlockFace.DOWN))) {
						return false;
					}
					// Use energy last
					if (!useEnergy(anchor, buildDelay))
						return false;
					int amount = stack.getAmount();
					if (amount == 1) {
						inventory.clear(i);
					} else {
						stack.setAmount(amount - 1);
						inventory.setItem(i, stack);
					}
					byte data = stack.getData().getData();
					nextBuild.getBlock().setTypeIdAndData(typeId, data, false);
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Returns the next location the Builder should build on.
	 * 
	 * @param anchor
	 *            The anchor of the Builder
	 * @return The next location that can be built on, or null if false.
	 */
	private BlockLocation nextBuild(final BlockLocation anchor) {

		BlockLocation head = anchor.getRelative(blueprint.getByIndex(
				Blueprint.primaryHeadIndex, yaw, Blueprint.mainModuleIndex));

		// Check below main head
		BlockLocation result = nextHeadBuild(head);
		// If no success there, check below left head.
		if (result == null && moduleIndices.contains(Blueprint.leftModuleIndex)) {
			head = anchor.getRelative(blueprint.getByIndex(
					Blueprint.leftHeadIndex, yaw, Blueprint.leftModuleIndex));
			result = nextHeadBuild(head);
		}
		// If no success there, check below right head.
		if (result == null
				&& moduleIndices.contains(Blueprint.rightModuleIndex)) {
			head = anchor.getRelative(blueprint.getByIndex(
					Blueprint.rightHeadIndex, yaw, Blueprint.rightModuleIndex));
			result = nextHeadBuild(head);
		}
		return result;
	}

	/**
	 * Returns the next build location below a specific head.
	 * 
	 * @param head
	 *            The head to look under.
	 * @return The next location that can be built on, or null if false.
	 */
	private BlockLocation nextHeadBuild(final BlockLocation head) {
		BlockVector stepDown = new BlockVector(BlockFace.DOWN);
		BlockLocation target = head.getRelative(stepDown);
		if (target.isEmpty()) {
			BlockLocation next;
			for (int depth = 0; depth < maxDepth; depth++) {
				next = target.getRelative(stepDown);
				if (next.isEmpty())
					target = next;
				else if (BlockData.isSolid(next.getTypeId()))
					return target;
				else
					return null;
			}
		}
		return null;
	}

	/**
	 * Moves the drill forward if there is empty space to move into, and ground
	 * to stand on.
	 * 
	 * @param anchor
	 *            The anchor of the Drill to move
	 * @return The new anchor location of the Drill, or null on failure.
	 */
	private BlockLocation doMove(final BlockLocation anchor) {
		// Check for ground at the new base
		BlockFace face = yaw.getFacing();
		BlockLocation newAnchor = anchor.getRelative(face);
		BlockLocation ground = newAnchor.getRelative(blueprint.getByIndex(
				Blueprint.centralBaseIndex, yaw, Blueprint.mainModuleIndex)
				.add(BlockFace.DOWN));
		if (!BlockData.isSolid(ground.getTypeId())) {
			return null;
		}

		// Collision detection
		for (int i : moduleIndices) {
			if (blueprint.detectCollision(anchor, face, yaw, i)) {
				return null;
			}
		}

		// Simulate a block place event to give protection plugins a chance to
		// stop the move
		if (!canPlace(newAnchor, Blueprint.primaryHeadIndex,
				Blueprint.headMaterial, Blueprint.mainModuleIndex)) {
			return null;
		}

		// Use energy
		if (!useEnergy(anchor, moveDelay)) {
			return null;
		}

		// Okay to move.
		moveByFace(anchor, face);

		return newAnchor;
	}

	/**
	 * Uses the given amount of energy and returns true if successful.
	 * 
	 * @param anchor
	 *            The anchor of the Builder
	 * @param energy
	 *            The amount of energy needed for the next action
	 * @return True if enough energy could be used up
	 */
	private boolean useEnergy(final BlockLocation anchor, final int energy) {
		while (currentEnergy < energy) {
			int newFuel = Fuel.consume((Furnace) anchor
					.getRelative(
							blueprint.getByIndex(Blueprint.furnaceIndex, yaw,
									Blueprint.mainModuleIndex)).getBlock()
					.getState());
			if (newFuel > 0) {
				currentEnergy += newFuel;
			} else {
				return false;
			}
		}
		currentEnergy -= energy;
		return true;
	}

	/**
	 * Simply checks the appropriate deactivate permission to determine whether
	 * the player may deactivate the Builder.
	 */
	public boolean playerDeActivate(final BlockLocation anchor, Player player) {
		if (this.player == player) {
			if (player.hasPermission("machinabuilder.deactivate-own"))
				return true;
		} else {
			if (player.hasPermission("machinabuilder.deactivate-all"))
				return true;
		}
		return false;
	}

	/**
	 * Returns the burning furnace to its normal state.
	 * 
	 * @param anchor
	 *            The anchor of the Drill being deactivated
	 */
	public void onDeActivate(final BlockLocation anchor) {
		// Set furnace to off state.
		Block furnace = anchor.getRelative(
				blueprint.getByIndex(Blueprint.furnaceIndex, yaw,
						Blueprint.mainModuleIndex)).getBlock();
		if (furnace.getType() == Material.BURNING_FURNACE) {
			Inventory inventory = ((Furnace) furnace.getState()).getInventory();
			Fuel.setFurnace(furnace, yaw.getOpposite().getFacing(), false,
					inventory);
		}
	}
}
