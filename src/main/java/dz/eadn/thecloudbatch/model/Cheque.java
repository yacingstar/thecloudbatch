package dz.eadn.thecloudbatch.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cheques")
public class Cheque {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cheque_sequence")
    private Long id;

    @Column(name = "cheque_number", nullable = false, unique = true)
    private String cheque_number;

    @Column(name = "beneficiary_rib", nullable = false)
    private String beneficiary_rib;

    @Column(name = "beneficiary_bank", nullable = false)
    private String beneficiary_bank;

    @Column(name = "sender_rib", nullable = false)
    private String sender_rib;

    @Column(name = "sender_bank", nullable = false)
    private String sender_bank;

    @Column(name = "amount", nullable = false)
    private Double amount;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCheque_number() {
        return cheque_number;
    }

    public void setCheque_number(String chequeNumber) {
        this.cheque_number = chequeNumber;
    }

    public String getBeneficiary_rib() {
        return beneficiary_rib;
    }

    public void setBeneficiary_rib(String beneficiaryRib) {
        this.beneficiary_rib = beneficiaryRib;
    }

    public String getBeneficiary_bank() {
        return beneficiary_bank;
    }

    public void setBeneficiary_bank(String beneficiaryBank) {
        this.beneficiary_bank = beneficiaryBank;
    }

    public String getSender_rib() {
        return sender_rib;
    }

    public void setSender_rib(String senderRib) {
        this.sender_rib = senderRib;
    }

    public String getSender_bank() {
        return sender_bank;
    }

    public void setSender_bank(String senderBank) {
        this.sender_bank = senderBank;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }
}