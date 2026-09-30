package pureneko.conveyor_belt_plus.blocks;

/** Transport domains supported by an interface; belt geometry is independent of this choice. */
public enum ChuteKind {
    ITEM, FLUID, UNIVERSAL;
    public boolean supports(boolean fluid) { return this == UNIVERSAL || fluid == (this == FLUID); }
}
