/* 
 * Copyright (C) 2017 bluew
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ch.gb.mem;

import java.util.HashMap;

import ch.gb.Component;
import ch.gb.GB;
import ch.gb.GBComponents;
import ch.gb.apu.APU;
import ch.gb.cpu.CPU;
import ch.gb.gpu.GPU;
import ch.gb.io.IOport;
import ch.gb.io.Joypad;
import ch.gb.io.Serial;
import ch.gb.io.SpriteDma;
import ch.gb.io.Timer;
import ch.gb.utils.Utils;
import java.util.Arrays;

/**
 * Contains CPU memory and manages all writes to it
 *
 * @author bluew
 *
 */
public class Memory implements Component {

    public static final int JOYP = 0xFF00;
    public static final int SB = 0xFF01;
    public static final int SC = 0xFF02;
    public static final int KEY1 = 0xFF4D;


    public byte[] vram; // 1 x 8kB , switchable in GBC
    private byte[] wram0; // 4kB
    public byte[] wram1; // 4kB, switchable in GBC
    private byte[] oam;// 0xA0 bytes OAM
    private byte[] hram;// 0x80 bytes high ram
    private byte ieReg;
    private byte ifReg;

    private int speedmode;

    private Mapper mbc;
    private CPU cpu;
    private GPU gpu;
    private APU apu;

    private String romInfo = "";
    // more IO
    private Timer timer;
    private Serial serial;
    private Joypad joy;
    private SpriteDma sprdma;
    private HashMap<Integer, IOport> io;

    private Cartridge rom;

    public Memory() {
       
        vram = new byte[0x2000];

        wram0 = new byte[0x2000];
        wram1 = new byte[0x2000];
        oam = new byte[0xA0];
        hram = new byte[0x80];

    }

    @Override
    public void reset() {
        romInfo = "";
        ieReg = 0;
        ifReg = 0;
        speedmode = 0;
        rom = null;
        mbc = null;

        Arrays.fill(vram, (byte) 0);
        Arrays.fill(wram0, (byte) 0);
        Arrays.fill(wram1, (byte) 0);
        Arrays.fill(oam, (byte) 0);
        Arrays.fill(hram, (byte) 0);

        timer.reset();
        joy.reset();
        serial.reset();
        sprdma.reset();
    }

    @Override
    public void connect(GBComponents comps) {
        cpu = comps.cpu;
        gpu = comps.gpu;
        apu = comps.apu;
        timer = comps.timer;
        joy = comps.joypad;
        serial = comps.serial;
        sprdma = comps.spriteDma;
        
        //map components to memory;
        io = new HashMap<Integer, IOport>();

        // set mapping
        io.put(Timer.DIVAddr, timer);
        io.put(Timer.TACAddr, timer);
        io.put(Timer.TIMAAddr, timer);
        io.put(Timer.TMAAddr, timer);

        io.put(Joypad.P1, joy);

        io.put(Serial.SB, serial);
        io.put(Serial.SC, serial);
    }

    public byte peek(int add) {
        //doesn't affect timing
        return readByte(add);
    }

    public void writeByte(int add, byte b) {

        if (add < 0x4000) {
            // 16kB Rom bank #0
            mbc.write(add, b);
        } else if (add < 0x8000) {
            // 16kB Rom switchable
            mbc.write(add, b);
        } else if (add < 0xA000) {
            // 8kB Video Ram
            vram[add - 0x8000] = b;
        } else if (add < 0xC000) {
            // 8kB exram
            mbc.write(add, b);
            //exram[(add - 0xA000) % exram.length] = b;
        } else if (add < 0xD000) {
            // 4kB WRAM 0
            wram0[add - 0xC000] = b;
        } else if (add < 0xE000) {
            // 4kB WRAM 1, switchable
            wram1[add - 0xD000] = b;
        } else if (add < 0xFE00) {
            // Partiall Mirror of WRAM 0
            wram0[add - 0xE000] = b;

        } else if (add < 0xFEA0) {
            // OAM
            oam[add - 0xFE00] = b;
        } else if (add < 0xFF00) {
            // empty and unusable
            // System.out.println("MemManager-> couldnt map write@empty" +
            // Utils.dumpHex(add)+"->"+Utils.dumpHex(b));
        } else if (add < 0xFF80) {
            // I/O ports
            if (add == CPU.IF_REG) {
                ifReg = (byte) (0xE0 | (b & 0x1F));
            } else if (add == KEY1) {// speed switch
                // just surpress since no gbc support yet
                speedmode = (b &= 1);

            } else if (add == SpriteDma.OAM_DMA) {
                sprdma.write(add, b);
                // System.out.println("LAUNCHING DMA");
            } else if (add >= 0xFF10 && add <= 0xFF3F) {
                // Sound
                apu.write(add, b);
            } else if (add >= 0xFF40 && add <= 0xFF4B) {
                // LCD
                gpu.write(add, b);
            } else if (add >= 0xFF00 && add <= 0xFF07) {
                // io port map
                io.get(add).write(add, b);
            } else {
                // System.out.println("MemManager-> couldnt map write@ioports" +
                // Utils.dumpHex(add));
            }
        } else if (add < 0xFFFF) {
            // HRAM
            hram[add - 0xFF80] = b;
        } else if (add == CPU.IE_REG) {
            // interrupt enable register
            ieReg = b;
        } else {
            System.err.println("Memory access violation in IO");
        }
        // System.out.println("Couldnt write to:"+Utils.dumpHex(add)+", out of range");
    }

    public byte readByte(int add) {
        add &= 0xFFFF;
        if (add < 0x4000) {
            // 16kB Rom bank #0
             //return rombanks[0][add];
            return mbc.read(add);  
        } else if (add < 0x8000) {
            // 16kB Rom switchable
            //return rombanks[1][add - 0x4000];
            return mbc.read(add);
        } else if (add < 0xA000) {
            // 8kB Video Ram
            return vram[add - 0x8000];
        } else if (add < 0xC000) {
            // 8kB EXRAM
            //return exram[(add - 0xA000) % exram.length];
            return mbc.read(add);
        } else if (add < 0xD000) {
            // 4kB WRAM 0
            return wram0[add - 0xC000];
        } else if (add < 0xE000) {
            // 4kB WRAM 1 switchable
            return wram1[add - 0xD000];
        } else if (add < 0xFE00) {
            // Partial mirror of WRAM 0
            return wram0[add - 0xE000];
        } else if (add < 0xFEA0) {
            // OAM
            return oam[add - 0xFE00];
        } else if (add < 0xFF00) {
            // empty and unusable
            System.out.println("MemManager-> couldnt map read@ empty" + Utils.dumpHex(add));
            return 0;
        } else if (add < 0xFF80) {
            // I/O ports
            if (add == CPU.IF_REG) {
                return ifReg;
            } else if (add == KEY1) {
                // just surpress
                return 0;
            } else if (add >= 0xFF10 && add <= 0xFF3F) {
                // Sound
                return apu.read(add);
            } else if (add >= 0xFF40 && add <= 0xFF4B) {
                // LCD
                return gpu.read(add);
            } else if (add >= 0xFF00 && add <= 0xFF07) {
                // io port map
                return io.get(add).read(add);
            } else {
                System.out.println("MemManager-> couldnt map read@ioports" + Utils.dumpHex(add));
                return 0;
            }
            // return 0;
        } else if (add < 0xFFFF) {
            // HRAM
            return hram[add - 0xFF80];
        } else {
            // interrupt enable register
            return ieReg;
        }
        // throw new RuntimeException("Couldnt decode Address:" +
        // Utils.dumpHex(add));
    }

    public void write2Byte(int add, int s) {
        writeByte(add, (byte) (s & 0xff));
        writeByte(add + 1, (byte) (s >> 8 & 0xff));
        // writeByte(add, (byte) (s >> 8 & 0xff));
        // writeByte(add + 1, (byte) (s & 0xff));

    }

    public int read2Byte(int add) {
        return ((readByte(add) & 0xff) | (readByte(add + 1) & 0xff) << 8);
        // return ((readByte(add ) & 0xff) << 8) | (readByte(add+1) & 0xff);
    }

    public void clock(int cpucycles) {
    }

    public void requestInterrupt(int i) {
        byte irq = readByte(CPU.IF_REG);
        irq = (byte) (irq | (1 << i));
        //System.out.println("IRQ?"+Utils.dumpHex(irq));
        writeByte(CPU.IF_REG, irq);
    }

    public void loadRom(String path) {
        rom = new Cartridge(path);
        //System.out.println(rom.getInformation());
        romInfo = rom.getInformation();
        System.out.println(romInfo);
        mbc = Mapper.createMBC(this, rom);
    }

    public void saveRam() {
        if (mbc != null) {
            mbc.saveRam();
        }
    }

    public String getRomInfo() {
        return romInfo;
    }

    public String romLoadPath() {
        return rom != null ? rom.getLoadPath() : null;
    }

}
