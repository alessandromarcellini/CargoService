package it.unibo.Hold;

public class Hold implements IHold {

	@Override
	public ICell[][] getHold() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ICell getCell(int i, int j) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public HoldState getState() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setState(HoldState state) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setCell(int i, int j, ICell cell) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public ISlot nextFreeSlot() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getSlotRow(int slotId) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getSlotCol(int slotId) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getIoportRow() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getIoportCol() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getHomeRow() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getHomeCol() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getStateDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void presetFull() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void presetEmpty() {
		// TODO Auto-generated method stub
		
	}
//	
//	private ICell[][] hold = new ICell[7][7];
//	private HoldState currentState = HoldState.DISENGAGED;
//
//	public Hold() {
//
//	    for (int i = 0; i < 7; i++) {
//	        for (int j = 0; j < 7; j++) {
//	            hold[i][j] = new Cell(CellType.FREE);
//	        }
//	    }
//
//	    // HOME
//	    hold[0][0] = new Cell(CellType.HOME);
//
//	    // SLOT
//	    hold[2][1] = new Slot(1);
//	    hold[5][1] = new Slot(2);
//	    hold[2][3] = new Slot(3);
//	    hold[5][3] = new Slot(4);
//	    hold[6][2] = new Slot(5);
//
//	    // OSTACOLI
//	    hold[3][1] = new Cell(CellType.OBSTACLE);
//	    hold[4][1] = new Cell(CellType.OBSTACLE);
//	    hold[3][3] = new Cell(CellType.OBSTACLE);
//	    hold[4][3] = new Cell(CellType.OBSTACLE);
//	    hold[5][2] = new Cell(CellType.OBSTACLE);
//
//	    // IOPORT
//	    hold[1][6] = new Cell(CellType.IOPORT);
//	}
//
//	@Override
//	public ICell[][] getHold() {
//		return this.hold;
//	}
//
//	@Override
//	public ICell getCell(int i, int j) {
//		return this.hold[i][j];
//	}
//
//	@Override
//	public HoldState getState() {
//		return this.currentState;
//	}
//
//	@Override
//	public void setState(HoldState state) {
//		this.currentState = state;
//		
//	}
//
//	@Override
//	public void setCell(int i, int j, ICell cell) {
//		this.hold[i][j] = cell;		
//	}
//	
//	
//	public ISlot nextFreeSlot() {
//		// Returns the first storage slot (id 1..4) that is currently free
//		// (no container placed), or null if all four are occupied.
//		// Slot 5 is temporary marking storage, not a reservable destination,
//		// so it is excluded on purpose.
//		for (int i = 0; i < hold.length; i++) {
//			for (int j = 0; j < hold[i].length; j++) {
//				ICell cell = hold[i][j];
//				if (cell instanceof ISlot) {
//					ISlot slot = (ISlot) cell;
//					if (slot.getId() >= 1 && slot.getId() <= 4 && slot.getContent() == null) {
//						return slot;
//					}
//				}
//			}
//		}
//		return null;
//	}
//	
//	public void setSlotToOccupied(int slotId) {
//		for (int i = 0 ; i < 7; i++) {
//			for (int j = 0 ; j < 7; j++) {
//				if (this.hold[i][j].getType() == CellType.SLOT) {
//				    Slot slot = (Slot) this.hold[i][j];
//
//				    if (slot.getId() == slotId) {
//				        slot.setContent(new Container());
//				    }
//				}
//			}
//		}
//	}
//
//	// ------------------------------------------------------------------
//	// Station coordinates (Sprint 1). The Hold is a business registry:
//	// slot states, hold state and station positions. The navigation map
//	// (obstacles, routing) is owned by the robot subsystem; the obstacle
//	// cells in the constructor are layout documentation only.
//	// ------------------------------------------------------------------
//
//	private int[] findSlot(int slotId) {
//		for (int i = 0; i < 7; i++) {
//			for (int j = 0; j < 7; j++) {
//				if (hold[i][j] instanceof ISlot && ((ISlot) hold[i][j]).getId() == slotId) {
//					return new int[] { i, j };
//				}
//			}
//		}
//		return new int[] { -1, -1 };
//	}
//
//	private int[] findCellOfType(CellType type) {
//		for (int i = 0; i < 7; i++) {
//			for (int j = 0; j < 7; j++) {
//				if (hold[i][j].getType() == type) {
//					return new int[] { i, j };
//				}
//			}
//		}
//		return new int[] { -1, -1 };
//	}
//
//	@Override
//	public int getSlotRow(int slotId) { return findSlot(slotId)[0]; }
//
//	@Override
//	public int getSlotCol(int slotId) { return findSlot(slotId)[1]; }
//
//	@Override
//	public int getIoportRow() { return findCellOfType(CellType.IOPORT)[0]; }
//
//	@Override
//	public int getIoportCol() { return findCellOfType(CellType.IOPORT)[1]; }
//
//	@Override
//	public int getHomeRow() { return findCellOfType(CellType.HOME)[0]; }
//
//	@Override
//	public int getHomeCol() { return findCellOfType(CellType.HOME)[1]; }
//
//	// ------------------------------------------------------------------
//	// Observability / controllability (Sprint 1 test support, P5).
//	// The description uses only letters, digits and underscores so it can
//	// safely travel as a message payload.
//	// ------------------------------------------------------------------
//
//	@Override
//	public String getStateDescription() {
//		StringBuilder sb = new StringBuilder();
//		sb.append(currentState.name().toLowerCase()); // lowercase: payload atoms must not start uppercase
//		for (int id = 1; id <= 4; id++) {
//			int[] pos = findSlot(id);
//			ISlot slot = (ISlot) hold[pos[0]][pos[1]];
//			sb.append("_slot").append(id).append(slot.getContent() == null ? "free" : "occupied");
//		}
//		return sb.toString();
//	}
//
//	@Override
//	public void presetFull() {
//		for (int id = 1; id <= 4; id++) {
//			setSlotToOccupied(id);
//		}
//	}
//
//	@Override
//	public void presetEmpty() {
//		for (int id = 1; id <= 5; id++) {
//			int[] pos = findSlot(id);
//			((ISlot) hold[pos[0]][pos[1]]).setContent(null);
//		}
//		this.currentState = HoldState.DISENGAGED;
//	}

}
