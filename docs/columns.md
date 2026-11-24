# Tick columns

Every column of `ticks/*.parquet`. A message that is not repeated, such as `player.hunger`, is
flattened into one column per field; a repeated field is a list column, whose element type is
described under [Schema](schema.md). An id column names
its vocabulary category.

<!-- schema:columns -->
