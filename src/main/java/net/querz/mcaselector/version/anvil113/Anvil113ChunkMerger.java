package net.querz.mcaselector.version.anvil113;

import net.querz.mcaselector.point.Point2i;
import net.querz.mcaselector.range.Range;
import net.querz.mcaselector.version.ChunkMerger;
import net.querz.mcaselector.version.Helper;
import net.querz.nbt.CompoundTag;
import net.querz.nbt.ListTag;
import net.querz.nbt.Tag;
import java.util.List;
import java.util.Map;

public class Anvil113ChunkMerger implements ChunkMerger {

	@Override
	public void mergeChunks(CompoundTag source, CompoundTag destination, List<Range> ranges, int yOffset) {
		mergeCompoundTagListsFromLevel(source, destination, ranges, yOffset, "Sections", c -> ((CompoundTag) c).getInt("Y"));
		mergeCompoundTagListsFromLevel(source, destination, ranges, yOffset, "Entities", c -> ((CompoundTag) c).getList("Pos").getInt(1) >> 4);
		mergeCompoundTagListsFromLevel(source, destination, ranges, yOffset, "TileEntities", c -> ((CompoundTag) c).getInt("y") >> 4);
		mergeCompoundTagListsFromLevel(source, destination, ranges, yOffset, "TileTicks", c -> ((CompoundTag) c).getInt("y") >> 4);
		mergeCompoundTagListsFromLevel(source, destination, ranges, yOffset, "LiquidTicks", c -> ((CompoundTag) c).getInt("y") >> 4);
		mergeListTagLists(source, destination, ranges, yOffset, "Lights");
		mergeListTagLists(source, destination, ranges, yOffset, "LiquidsToBeTicked");
		mergeListTagLists(source, destination, ranges, yOffset, "ToBeTicked");
		mergeListTagLists(source, destination, ranges, yOffset, "PostProcessing");
		mergeStructures(source, destination, ranges, yOffset);

		// we need to fix entity UUIDs, because Minecraft doesn't like duplicates
		fixEntityUUIDs(Helper.levelFromRoot(destination));
	}

	private void mergeStructures(CompoundTag source, CompoundTag destination, List<Range> ranges, int yOffset) {
		CompoundTag sourceStarts = Helper.tagFromCompound(Helper.tagFromLevelFromRoot(source, "Structures", new CompoundTag()), "Starts", new CompoundTag());
		CompoundTag destinationStarts = Helper.tagFromCompound(Helper.tagFromLevelFromRoot(destination, "Structures", new CompoundTag()), "Starts", new CompoundTag());

		if (destinationStarts.size() != 0) {
			// remove BBs from destination
			for (Map.Entry<String, Tag> start : destinationStarts) {
				ListTag children = Helper.tagFromCompound(start.getValue(), "Children", null);
				if (children != null) {
					child: for (int i = 0; i < children.size(); i++) {
						CompoundTag child = children.getCompound(i);
						int[] bb = Helper.intArrayFromCompound(child, "BB");
						if (bb != null && bb.length == 6) {
							for (Range range : ranges) {
								if (range.contains(bb[1] >> 4) && range.contains(bb[4] >> 4)) {
									children.remove(i);
									i--;
									continue child;
								}
							}
						}
					}
				}

				// if we removed all children, we check the start BB
				if (children == null || children.size() == 0) {
					int[] bb = Helper.intArrayFromCompound(start.getValue(), "BB");
					if (bb != null && bb.length == 6) {
						for (Range range : ranges) {
							if (range.contains(bb[1] >> 4) && range.contains(bb[4] >> 4)) {
								CompoundTag emptyStart = new CompoundTag();
								emptyStart.putString("id", "INVALID");
								destinationStarts.put(start.getKey(), emptyStart);
								break;
							}
						}
					}
				}
			}
		}

		// add BBs from source to destination
		// if child BB doesn't exist in destination, we copy start over to destination
		for (Map.Entry<String, Tag> start : sourceStarts) {
			ListTag children = Helper.tagFromCompound(start.getValue(), "Children", null);
			if (children != null) {
				child:
				for (int i = 0; i < children.size(); i++) {
					CompoundTag child = children.getCompound(i);
					int[] bb = Helper.intArrayFromCompound(child, "BB");
					if (bb == null) {
						continue;
					}
					for (Range range : ranges) {
						if (range.contains(bb[1] >> 4 - yOffset) || range.contains(bb[4] >> 4 - yOffset)) {
							CompoundTag destinationStart = Helper.tagFromCompound(destinationStarts, start.getKey(), null);
							if (destinationStart == null || "INVALID".equals(destinationStart.getString("id"))) {
								destinationStart = ((CompoundTag) start.getValue()).copy();

								// we need to remove the children, we don't want all of them
								ListTag clonedDestinationChildren = Helper.tagFromCompound(destinationStart, "Children", null);
								if (clonedDestinationChildren != null) {
									clonedDestinationChildren.clear();
								}
								destinationStarts.put(start.getKey(), destinationStart);
							}

							ListTag destinationChildren = Helper.tagFromCompound(destinationStarts.get(start.getKey()), "Children", null);
							if (destinationChildren == null) {
								destinationChildren = new ListTag();
								destinationStart.put("Children", destinationChildren);
							}

							destinationChildren.add(children.get(i));
							continue child;
						}
					}
				}
			}
		}
	}

	@Override
	public CompoundTag newEmptyChunk(Point2i absoluteLocation, int dataVersion) {
		CompoundTag root = new CompoundTag();
		CompoundTag level = new CompoundTag();
		level.putInt("xPos", absoluteLocation.getX());
		level.putInt("zPos", absoluteLocation.getZ());
		level.putString("Status", "postprocessed");
		root.put("Level", level);
		root.putInt("DataVersion", dataVersion);
		return root;
	}
}
