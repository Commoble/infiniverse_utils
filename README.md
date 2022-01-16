A mod for Minecraft Forge that adds extra server utilities for adding and removing dimensions at runtime. This mod is not required to be present on clients.

Built jars are available here: <https://www.curseforge.com/minecraft/mc-mods/infiniverse-utils>

This mod requires the Infiniverse mod, which is available here: <https://www.curseforge.com/minecraft/mc-mods/infiniverse>

## Configuration

The first time minecraft is launched with Infiniverse Utils installed, a config file will be created at `<yourminecraftinstance>/config/infiniverse_utils-common.toml`. The default config file is as follows:

```toml
#Minimum permission level needed to create/remove dimensions and use related commands.
#Defaults to 4 (server admin)
#Range: 0 ~ 4
editDimensionRegistryPermissionLevel = 4
```

## Commands

### /infiniverse recreate

`/infiniverse recreate <oldDimensionName: Dimension> <newDimensionName: Dimension> [autoseed|copyseed|randomseed|seed] [seed: String]`

This command recreates an existing dimension using the same chunk generator. The arguments are as follows:

* `oldDimensionName` -- The namespaced ID of the dimension being recreated. If a namespace is not specified, "minecraft" will be used as the namespace.
* `newDimensionName` -- The namespaced ID of the new copy of the dimension. Cannot use any existing dimension IDs.
* `[autoseed|copyseed|randomseed|seed]` -- Optional argument, indicates how to generate the seed of the new dimension. Defaults to copyseed if not specified.
    * `autoseed` -- Generates a seed from the server's primary seed and the new ID of the new dimension.
    * `copyseed` -- Uses the same seed as the original dimension being recreated.
    * `randomseed` -- Uses a random seed.
    * `seed` -- Accepts one additional [seed: String] argument, accepting a numeric or string seed in the same manner as the singleplayer create world menu.
* `[seed: String]` Only used with the `seed` seed type above

Be aware that if a dimension is created with the same ID as a previously-existing dimension that was unregistered using the remove command below or similar means, any region files or other data that still exist on the server will be used for the new dimension, causing the previously-existing dimension's chunks to be present in the "new" dimension. Freshly generated chunks in the new dimension will use the new chunk generator and seed. This may cause discontinuities between old chunks and new chunks.

### /infiniverse remove

`/infiniverse remove <oldDimensionName: Dimension>`

This command removes a dimension from the server's dimension registry. Doing so will eject all players present in that dimension to their respawn point (or to the overworld spawn if their respawn point is unavailable). Dimensions removed in this way will no longer be accessible and will no longer tick, until readded by `/infiniverse recreate`, or by another mod that can add dimensions at runtime. This does not delete the region files or other persistant data for that dimension, so if the dimension is recreated by any means, any changes or buildings made by players will still be there.

Dimensions defined via dimension json can be temporarily removed by this method, but will be automatically reregistered by minecraft the next time the server starts if the dimension jsons are still present in the server's datapacks. The three vanilla dimensions (overworld, nether, and end) cannot be removed by this command.
