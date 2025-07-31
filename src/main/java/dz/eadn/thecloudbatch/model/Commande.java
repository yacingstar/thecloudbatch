package dz.eadn.thecloudbatch.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "commandes")
public class Commande {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    private short commande_number;
    private String commande_type;
    private short sender_bank;
    private short receiver_bank;
    private short source;
    private short lot_number;
    private short operation_type;

    public Commande() {
    }

    public Commande(long id, short commande_number, String commande_type, short sender_bank, short receiver_bank, short source, short lot_number, short operation_type) {
        this.id = id;
        this.commande_number = commande_number;
        this.commande_type = commande_type;
        this.sender_bank = sender_bank;
        this.receiver_bank = receiver_bank;
        this.source = source;
        this.lot_number = lot_number;
        this.operation_type = operation_type;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public short getCommande_number() {
        return commande_number;
    }

    public void setCommande_number(short commande_number) {
        this.commande_number = commande_number;
    }

    public String getCommande_type() {
        return commande_type;
    }

    public void setCommande_type(String commande_type) {
        this.commande_type = commande_type;
    }

    public short getSender_bank() {
        return sender_bank;
    }

    public void setSender_bank(short sender_bank) {
        this.sender_bank = sender_bank;
    }

    public short getReceiver_bank() {
        return receiver_bank;
    }

    public void setReceiver_bank(short receiver_bank) {
        this.receiver_bank = receiver_bank;
    }

    public short getSource() {
        return source;
    }

    public void setSource(short source) {
        this.source = source;
    }

    public short getLot_number() {
        return lot_number;
    }

    public void setLot_number(short lot_number) {
        this.lot_number = lot_number;
    }

    public short getOperation_type() {
        return operation_type;
    }

    public void setOperation_type(short operation_type) {
        this.operation_type = operation_type;
    }

    @Override
    public String toString() {
        return "Commande{" +
                "id=" + id +
                ", commande_number=" + commande_number +
                ", commande_type='" + commande_type + '\'' +
                ", sender_bank=" + sender_bank +
                ", receiver_bank=" + receiver_bank +
                ", source=" + source +
                ", lot_number=" + lot_number +
                ", operation_type=" + operation_type +
                '}';
    }
}
