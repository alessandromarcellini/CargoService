package it.unibo.Hold;

public interface IHold {
	
	public ICell[][] getHold();
	public ICell getCell(int i, int j);
	
	public HoldState getState();
	public void setState(HoldState state);
	public void setCell(int i, int j, ICell cell);
	
	public ISlot nextFreeSlot(); // if returns null, all slots are taken

	// --- station coordinates (business registry; the navigation map is owned by the robot subsystem) ---
	public int getSlotRow(int slotId);
	public int getSlotCol(int slotId);
	public int getIoportRow();
	public int getIoportCol();
	public int getHomeRow();
	public int getHomeCol();

	// --- observability / controllability (Sprint 1 test support, P5) ---
	public String getStateDescription(); // e.g. "disengaged_slot1free_slot2occupied_slot3free_slot4free"
	public void presetFull();            // occupies slots 1..4 (test Given)
	public void presetEmpty();           // empties all slots and sets DISENGAGED (test Given)
}