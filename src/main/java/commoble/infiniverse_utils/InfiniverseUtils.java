package commoble.infiniverse_utils;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.DynamicOps;

import commoble.infiniverse.api.InfiniverseAPI;
import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint.DisplayTest;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.network.NetworkConstants;

@Mod(InfiniverseUtils.MODID)
public class InfiniverseUtils
{
	public static final String MODID = "infiniverse_utils";
	
	private static final String OLD_DIMENSION_NAME_ARG = "oldDimensionName";
	private static final String NEW_DIMENSION_NAME_ARG = "newDimensionName";
	private static final String SEED_ARG = "seed";
	private static final Collection<String> DIMENSION_EXAMPLES = List.of("namespace:id", "super_nether", "12345");
	private static final Collection<String> SEED_EXAMPLES = List.of("12345", "a bunch of words", "\"words! with. symbols?\"");
	
	private final CommonConfig commonConfig;
	
	public InfiniverseUtils()
	{
		IEventBus forgeBus = MinecraftForge.EVENT_BUS;
		ModLoadingContext modContext = ModLoadingContext.get();

		// mod is not required to be on both sides, greenlight mismatched servers in client's server list
		modContext.registerExtensionPoint(DisplayTest.class,
			() -> new DisplayTest(
				() -> NetworkConstants.IGNORESERVERONLY,
				(s, networkBool) -> true));

		final org.apache.commons.lang3.tuple.Pair<CommonConfig, ForgeConfigSpec> entry = new ForgeConfigSpec.Builder()
			.configure(CommonConfig::create);
		this.commonConfig = entry.getLeft();
		modContext.registerConfig(Type.COMMON, entry.getRight());
		
		forgeBus.addListener(this::onRegisterCommands);
	}
	
	private void onRegisterCommands(RegisterCommandsEvent event)
	{
		CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
		
		dispatcher.register(Commands.literal("infiniverse")
			.requires(stack -> stack.hasPermission(this.commonConfig.editDimensionRegistryPermissionLevel().get()))
			// infiniverse recreate <oldDimensionName: Dimension> <newDimensionName: Dimension> [autoseed|copyseed|randomseed|seed] [seed: String]
			.then(Commands.literal("recreate")
				.then(Commands.argument(OLD_DIMENSION_NAME_ARG, DimensionArgument.dimension())
					.then(Commands.argument(NEW_DIMENSION_NAME_ARG, ResourceLocationArgument.id())
						.suggests((context,builder) -> SharedSuggestionProvider.suggest(DIMENSION_EXAMPLES, builder))
						.executes(this.makeCopyDimensionCommand(this::copyChunkGenerator))
						.then(Commands.literal("autoseed")
							.executes(this.makeCopyDimensionCommand(this::copyChunkGeneratorWithAutoSeed)))
						.then(Commands.literal("copyseed")
							.executes(this.makeCopyDimensionCommand(this::copyChunkGenerator)))
						.then(Commands.literal("randomseed")
							.executes(this.makeCopyDimensionCommand(this::copyChunkGeneratorWithRandomSeed)))
						.then(Commands.literal("seed")
							.then(Commands.argument(SEED_ARG, StringArgumentType.greedyString())
								.suggests((context,builder) -> SharedSuggestionProvider.suggest(SEED_EXAMPLES, builder))
								.executes(this.makeCopyDimensionCommand(this::copyChunkGeneratorWithSeed)))))))
			// infiniverse remove <oldDimensionName: Dimension>
			.then(Commands.literal("remove")
				.then(Commands.argument(OLD_DIMENSION_NAME_ARG, DimensionArgument.dimension())
					.executes(this::removeDimension))));
	}
	
	private Command<CommandSourceStack> makeCopyDimensionCommand(ChunkGeneratorFactory factory)
	{
		return context ->
		{
			ResourceLocation newID = ResourceLocationArgument.getId(context, NEW_DIMENSION_NAME_ARG);
			CommandSourceStack stack = context.getSource();
			MinecraftServer server = stack.getServer();
			ResourceKey<Level> newKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, newID);
			
			// make sure new dimension's new name isn't already in use
			ServerLevel existingNewLevel = server.getLevel(newKey);
			if (existingNewLevel != null)
			{
				throw new SimpleCommandExceptionType(new LiteralMessage(String.format("Error copying dimension: ID %s is already in use", newID))).create();
			}
			
			ServerLevel oldLevel = DimensionArgument.getDimension(context, OLD_DIMENSION_NAME_ARG);
			Holder<DimensionType> typeHolder = oldLevel.dimensionTypeRegistration();
			
			Supplier<LevelStem> dimensionFactory = ()->
				new LevelStem(typeHolder, factory.create(server, newKey, context, oldLevel));

			InfiniverseAPI.get().getOrCreateLevel(server, newKey, dimensionFactory);
			
			stack.sendSuccess(new TextComponent("Created dimension with id " + newID), false);
			
			return 1;
		};
	}
	
	private ChunkGenerator copyChunkGenerator(MinecraftServer server, ResourceKey<Level> key, CommandContext<CommandSourceStack> context, ServerLevel oldLevel)
	{
		// deep-copy the chunk generator (the chunk generator seed isn't necessarily preserved in chunk generators)
		DynamicOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
		ChunkGenerator oldChunkGenerator = oldLevel.getChunkSource().getGenerator();
		return ChunkGenerator.CODEC.encodeStart(ops, oldChunkGenerator)
			.flatMap(nbt -> ChunkGenerator.CODEC.parse(ops, nbt))
			.getOrThrow(false, s ->
			{
				throw new CommandRuntimeException(new TextComponent(String.format("Error copying dimension: %s", s)));			
			});
	}
	
	private ChunkGenerator copyChunkGeneratorWithAutoSeed(MinecraftServer server, ResourceKey<Level> key, CommandContext<CommandSourceStack> context, ServerLevel oldLevel)
	{
		long newSeed = server.overworld().getSeed() + key.location().hashCode();
		return oldLevel.getChunkSource().getGenerator().withSeed(newSeed);
	}
	
	private ChunkGenerator copyChunkGeneratorWithRandomSeed(MinecraftServer server, ResourceKey<Level> key, CommandContext<CommandSourceStack> context, ServerLevel oldLevel)
	{
		ServerLevel overworld = server.overworld();
		long newSeed = overworld.random.nextLong();
		return oldLevel.getChunkSource().getGenerator().withSeed(newSeed);
	}
	
	private ChunkGenerator copyChunkGeneratorWithSeed(MinecraftServer server, ResourceKey<Level> key, CommandContext<CommandSourceStack> context, ServerLevel oldLevel)
	{
		String seedString = StringArgumentType.getString(context, SEED_ARG);
		// vanilla behavior is to try to parse the string as a long first, otherwise use its hashcode
		long seed;
		try
		{
			seed = Long.parseLong(seedString);
		}
		catch(NumberFormatException e)
		{
			seed = seedString.hashCode();
		}
		return oldLevel.getChunkSource().getGenerator().withSeed(seed);
	}
	
	private int removeDimension(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
	{
		ServerLevel level = DimensionArgument.getDimension(context, OLD_DIMENSION_NAME_ARG);
		ResourceKey<Level> key = level.dimension();
		InfiniverseAPI.get().markDimensionForUnregistration(context.getSource().getServer(), key);
		context.getSource().sendSuccess(new TextComponent("Removing dimension with id " + key.location()), false);
		return 1;
	}
	
	private static interface ChunkGeneratorFactory
	{
		public abstract ChunkGenerator create(MinecraftServer server, ResourceKey<Level> key, CommandContext<CommandSourceStack> context, ServerLevel oldLevel);
	}
}
