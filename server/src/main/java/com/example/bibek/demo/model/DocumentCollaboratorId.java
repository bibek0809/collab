package com.example.bibek.demo.model;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DocumentCollaboratorId implements Serializable {
    private String documentId;
    private String userId;
}
