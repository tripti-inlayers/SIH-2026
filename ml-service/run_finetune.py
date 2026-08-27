from detector import SpamMessageDetector

def main():
    print("Loading original model...")
    detector = SpamMessageDetector(model_path="mshenoda/roberta-spam")
    
    print("Fine-tuning on custom OTP dataset for 10 epochs...")
    # Train using the CSV we just created
    detector.train(train_data_path="finetune_data.csv", num_epochs=10, batch_size=4)
    
    print("Saving fine-tuned model to './finetuned_model'...")
    detector.save_model("./finetuned_model")
    
    print("Done! You can now use the fine-tuned model.")

if __name__ == "__main__":
    main()
