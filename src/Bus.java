import java.util.Arrays;



public class Bus {

    // Dispositivos no barramento
    public CPU6502 cpu;
    // RAM fake
    public int[] ram = new int[64*1024];


    

    public Bus(){
        // Conectar CPU ao barramento de comunicação
        cpu = new CPU6502();
        cpu.ConnectBus(this);

        //Limpar o conteudo da Ram
        Arrays.fill(ram,0);

    }

    public void write(int addr,int data){
        if (addr >=0x0000 && addr <= 0xFFFF){
            System.out.println("write in "+addr+" data:"+data);
            this.ram[addr]=data;
        }

    }

    public int read(int addr, boolean bReadOnly){
        if ( addr >=0x0000 && addr <=0xFFFF){
            //System.out.println(ram[addr]+" addr:"+addr);
            return this.ram[addr];
        }
        return 0;

    }


}
