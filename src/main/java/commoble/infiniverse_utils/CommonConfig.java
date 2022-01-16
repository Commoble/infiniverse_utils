package commoble.infiniverse_utils;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public record CommonConfig(IntValue editDimensionRegistryPermissionLevel)
{
	public static CommonConfig create(ForgeConfigSpec.Builder builder)
	{
		builder.comment("Minimum permission level needed to create/remove dimensions and use related commands.",
			"Defaults to 4 (server admin)");
		IntValue minCommandPermissionLevel = builder.defineInRange("editDimensionRegistryPermissionLevel", 4, 0, 4);
		return new CommonConfig(minCommandPermissionLevel);
	}
}
