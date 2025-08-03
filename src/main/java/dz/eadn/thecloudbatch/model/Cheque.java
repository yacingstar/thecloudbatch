package dz.eadn.thecloudbatch.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cheques")
public class Cheque {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cheque_sequence")
    private Long id;

    @Column(name = "cheque_number", nullable = false, unique = true)
    private long cheque_number;
    
    @Column(name = "rio", nullable = false)
    private String rio;
    
    @Column(name= "operation_type", nullable = false)
    private short operation_type;

    @Column(name = "beneficiary_rib", nullable = false)
    private String beneficiary_rib;

    @Column(name = "beneficiary_bank", nullable = false)
    private short beneficiary_bank;

    @Column(name = "sender_rib", nullable = false)
    private String sender_rib;

    @Column(name = "sender_bank", nullable = false)
    private short sender_bank;

    @Column(name = "amount", nullable = false)
    private int amount;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getCheque_number() {
        return cheque_number;
    }
    public short getOperation_type() {
        return operation_type;
    }

    public void setOperation_type(short operation_type) {
        this.operation_type = operation_type;
    }

    public void setCheque_number(long chequeNumber) {
        this.cheque_number = chequeNumber;
    }
    
    public String getRio() {
        return rio;
    }

    public void setRio(String rio) {
        this.rio = rio;
    }

    public String getBeneficiary_rib() {
        return beneficiary_rib;
    }

    public void setBeneficiary_rib(String beneficiaryRib) {
        this.beneficiary_rib = beneficiaryRib;
    }

    public short getBeneficiary_bank() {
        return beneficiary_bank;
    }

    public void setBeneficiary_bank(short beneficiaryBank) {
        this.beneficiary_bank = beneficiaryBank;
    }

    public String getSender_rib() {
        return sender_rib;
    }

    public void setSender_rib(String senderRib) {
        this.sender_rib = senderRib;
    }

    public short getSender_bank() {
        return sender_bank;
    }

    public void setSender_bank(short senderBank) {
        this.sender_bank = senderBank;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }
}