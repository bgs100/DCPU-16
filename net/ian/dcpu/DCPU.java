package net.ian.dcpu;

import java.util.List;
import java.util.Map;

public class DCPU {
	public Cell[] register;
	public Cell[] memory;
	public Cell SP, PC, O;
	public boolean running;
	public int instructionCount = 0;
	
	public static final boolean debug = false;
	
	public Map<Integer, String> labels;
	
	public enum Register { A, B, C, X, Y, Z, I, J };
	
	public DCPU() {
		this(new int[0]);
	}

	public DCPU(int[] mem) {
		register = new Cell[11];
		for (int i = 0; i < 11; i++)
			register[i] = new Cell(0);
		memory = new Cell[0x10000]; // 0x10000 words in size
		for (int i = 0; i < 0x10000; i++)
			memory[i] = new Cell(i < mem.length ? mem[i] : 0);

		SP = new Cell(0);
		PC = new Cell(0);
		O = new Cell(0);
	}
	
	public DCPU(List<Integer> mem) {
		this(integersToInts(mem));
	}
	
	public void setMemory(List<Integer> listMem) {
		int[] mem = integersToInts(listMem);
		for (int i = 0; i < mem.length; i++)
			memory[i].value = mem[i];
	}
	
	// This is here because Java wants constructor calls to be the first statement in another constructor (see above).
	private static int[] integersToInts(List<Integer> mem) {
		Integer[] integers = mem.toArray(new Integer[0]);
		int[] ints = new int[integers.length];
		for (int i = 0; i < ints.length; i++)
			ints[i] = integers[i];
		return ints;
	}
	
	private void debug(Object o) {
		if (debug)
			System.out.print(o);
	}
	
	private void debugln(Object o) {
		if (debug)
			System.out.println(o);
	}
	
	private void debugf(String s, Object... o) {
		if (debug)
			System.out.printf(s, o);
	}

	public Cell getRegister(Register r) {
		return register[r.ordinal()];
	}
	
	public void setRegister(Register r, int value) {
		register[r.ordinal()].value = value;
	}
	
	private Cell handleArgument(int code) {
		debugf("0x%s: ", Integer.toHexString(code));
		if (code >= 0x0 && code <= 0x7) {
			debug(Register.values()[code]);
			return register[code];
		} else if (code >= 0x8 && code <= 0xf) {
			debugf("[%s]", Register.values()[code - 0x8]);
			return memory[register[code - 0x8].value];
		} else if (code >= 0x10 && code <= 0x17) {
			debugf("[next word + %s]", Register.values()[code - 0x10]);
			return memory[memory[PC.value++].value + register[code - 0x10].value];
		} else if (code == 0x18) {
			debug("POP");
			return memory[SP.value++];
		} else if (code == 0x19) {
			debug("PEEK");
			return memory[SP.value];
		} else if (code == 0x1a) {
			debug("PUSH");
			SP.value = (SP.value == 0) ? 0xffff : (SP.value - 1);
			return memory[SP.value];
		} else if (code == 0x1b) {
			debug("SP");
			return SP;
		} else if (code == 0x1c) {
			debug("PC");
			return PC;
		} else if (code == 0x1d) {
			debug("O");
			return O;
		} else if (code == 0x1e) {
			debug("[next word]");
			return memory[memory[PC.value++].value];
		} else if (code == 0x1f) {
			debug("next word (literal)");
			return new Cell(memory[PC.value++].value);
		}
		debug("literal: " + (code - 0x20));
		return new Cell(code - 0x20);
	}
	
	private void skipInstruction() {
		// This skips to the end of the next instruction - used in IF operations.
		int instruction = memory[PC.value++].value;
		int a, b = -1;
		if ((instruction & 0xf) == 0) {
			// Non-basic opcode
			a = instruction >> 10 & 0x3f;
		} else {
			// Basic opcode
			a = instruction >> 4 & 0x3f;
			b = instruction >> 10 & 0x3f;
		}
		
		if ((a >= 0x10 && a <= 0x17) || a == 0x1e || a == 0x1f)
			PC.value++;
		if ((b >= 0x10 && b <= 0x17) || b == 0x1e || b == 0x1f)
			PC.value++;
	}
	
	private void processBasic(int opcode, Cell cellA, Cell cellB) {
		int a = cellA.value;
		int b = cellB.value;
		switch (opcode) {
		case 0x1: // SET a to b
			debugln("SET");
			a = b;
			break;
		case 0x2: // ADD b to a
			debugln("ADD");
			O.value = (a += b) > 0xffff ? 1 : 0;
			a &= 0xffff;
			break;
		case 0x3: // SUBTRACT b from a
			debugln("SUB");
			O.value = (a -= b) < 0 ? 0xffff : 0;
			a = a < 0 ? 0 : a;
			break;
		case 0x4: // MUL multiplies a by b
			debugln("MUL");
			O.value = (a *= b) >> 16 & 0xffff;
			a &= 0xffff;
			break;
		case 0x5: // DIV divides a by b
			debugln("DIV");
			if (b == 0) {
				a = 0;
				O.value = 0;
			} else {
				O.value = ((a << 16) / b) & 0xffff;
				a /= b;
			}
			break;
		case 0x6: // MOD (sets a to a % b)
			debugln("MOD");
			a = (b == 0) ? 0 : a % b;
			break;
		case 0x7: // SHL shifts a left by b
			debugln("SHL");
			O.value = a << b >> 16 & 0xffff;
			a = a << b & 0xffff;
			break;
		case 0x8: // SHR shifts a right by b
			debugln("SHR");
			O.value = a << 16 >> b & 0xffff;
			a >>= b;
			break;
		case 0x9: // AND sets a to a & b
			debugln("AND");
			a &= b;
			break;
		case 0xa: // BOR sets a to a | b
			debugln("BOR");
			a |= b;
			break;
		case 0xb: // XOR sets a to a ^ b
			debugln("XOR");
			a ^= b;
			break;
		case 0xc: // IFE performs next instruction if a == b
			debugln("IFE");
			if (a != b)
				skipInstruction();
			break;
		case 0xd: // IFN performs next instruction if a != b
			debugln("IFN");
			if (a == b)
				skipInstruction();
			break;
		case 0xe: // IFG performs next instruction if a > b
			debugln("IFG");
			if (a <= b)
				skipInstruction();
			break;
		case 0xf: // IFB performs next instructions if (a & b) != 0
			debugln("IFB");
			if ((a & b) == 0)
				skipInstruction();
			break;
		default:
			debugln("INVALID BASIC OPERATION");
		}
		cellA.value = a;
		cellB.value = b;
	}

	private void processSpecial(int opcode, Cell a) {
		switch (opcode) {
		case 0x0: // EXIT custom code, makes the processor stop.
			debugln("EXIT");
			running = false;
			break;
		case 0x1: // JSR pushes the address of the next instruction to the stack, sets PC to a
			debugln("JSR");
			SP.value = (SP.value == 0) ? 0xffff : (SP.value - 1);
			memory[SP.value].value = PC.value;
			PC.value = a.value;
			break;
		default:
			debug("INVALID SPECIAL OPERATION: " + opcode);
		}
	}
	
	@SuppressWarnings("unused")
	public void cycle() {
		if (debug && labels != null && labels.containsKey(PC.value)) {
			System.out.println(labels.get(PC.value));
		}
		
		int instruction = memory[PC.value].value;
		int opcode;
		int rawA, rawB = -1;
		if ((instruction & 0xf) == 0) {
			// Non-basic opcode
			instruction >>= 4; // Lower 4 bits are unset.
			opcode = instruction & 0x3f;
			rawA = instruction >> 6 & 0x3f;
		} else {
			// Basic opcode
			opcode = instruction & 0xf;
			rawA = instruction >> 4 & 0x3f;
			rawB = instruction >> 10 & 0x3f;
		}
		
		PC.value++;
		
		debug("A: ");
		Cell a = handleArgument(rawA), b = null;
		debugln(" = " + a.value);
		if (rawB != -1) {
			debug("B: ");
			b = handleArgument(rawB);
			debugln(" = " + b.value);
		}
		
		if (b != null)
			processBasic(opcode, a, b);
		else
			processSpecial(opcode, a);
		
		instructionCount++;
	}
}